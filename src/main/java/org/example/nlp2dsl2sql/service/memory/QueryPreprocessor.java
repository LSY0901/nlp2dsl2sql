package org.example.nlp2dsl2sql.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 会话多轮记忆的唯一入口：读侧做追问消歧，写侧沉淀语义快照与全量轮次归档。
 * <p>
 * 五种编排形态（V2 固定管线 / ReAct / Skill / Planner / A2A Host）共用同一份
 * 快照，因为它们共享同一套 DSL 生成工具与提示词，产物是模式无关的。
 * <p>
 * 职责切分：
 * <ul>
 *   <li>{@link #rememberSnapshotOnly}：仅在 SQL 子查询产出 DSL 时更新 Redis {@code snapshot} 键，
 *       确保复合问题内的后续 SQL 子问题或下一轮追问立即继承，且不被 SOP/闲聊覆盖；</li>
 *   <li>{@link #rememberTurn}：在对话流完成时汇总「一问一答一轮」，有 DSL 时更新快照，并写穿 PG {@code memory_session_archive}。</li>
 * </ul>
 */
@Slf4j
@Component
public class QueryPreprocessor {

    private final SessionSnapshotStore store;
    private final QueryContextRewriter rewriter;
    private final MemoryArchiveWriter archiveWriter;
    private final UserKeyResolver userKeyResolver;
    private final MemoryProfileService profileService;
    private final MemoryProperties memoryProperties;

    /**
     * 兼容 5 参构造（仅供单测便捷构造）。
     */
    public QueryPreprocessor(SessionSnapshotStore store,
                             QueryContextRewriter rewriter,
                             MemoryArchiveWriter archiveWriter,
                             UserKeyResolver userKeyResolver,
                             MemoryProfileService profileService) {
        this(store, rewriter, archiveWriter, userKeyResolver, profileService, new MemoryProperties());
    }

    /**
     * @param store            短期快照与轮次存储
     * @param rewriter         追问改写器
     * @param archiveWriter    PG 流水异步写穿器
     * @param userKeyResolver  userId 派生器
     * @param profileService   长期画像读取服务
     * @param memoryProperties 记忆配置
     */
    @Autowired
    public QueryPreprocessor(SessionSnapshotStore store,
                             QueryContextRewriter rewriter,
                             MemoryArchiveWriter archiveWriter,
                             UserKeyResolver userKeyResolver,
                             MemoryProfileService profileService,
                             MemoryProperties memoryProperties) {
        this.store = store;
        this.rewriter = rewriter;
        this.archiveWriter = archiveWriter;
        this.userKeyResolver = userKeyResolver;
        this.profileService = profileService;
        this.memoryProperties = memoryProperties != null ? memoryProperties : new MemoryProperties();
    }

    /**
     * 读侧：把"那四年级呢"这类依赖上下文的追问补全为自包含问句。
     *
     * @param sessionId 会话 ID；为空表示无记忆可用，直接透传原问句
     * @param question  用户原始问句
     * @return 生效问句（命中追问特征且有上一轮快照时才可能变化）
     */
    public String preprocess(String sessionId, String question) {
        return preprocess(sessionId, null, question);
    }

    /**
     * 读侧：追问补全（带用户身份，命中改写分支时注入该用户的长期画像）。
     *
     * @param sessionId 会话 ID
     * @param userId    归属用户 ID，可为 null（按 sessionId 派生）
     * @param question  用户原始问句
     * @return 生效问句
     */
    public String preprocess(String sessionId, String userId, String question) {
        if (question == null || question.isBlank()) {
            return question;
        }
        String trimmed = question.trim();
        if (sessionId == null || sessionId.isBlank()) {
            return trimmed;
        }
        SemanticSessionSnapshot previous = store.get(sessionId);
        if (previous == null) {
            // 无上一轮快照时改写器必然不改写，直接透传（也免去一次画像 PG 查询）
            return trimmed;
        }
        String profileJson = loadProfileQuietly(
                userKeyResolver.resolve(sessionId, userId));
        String effective = rewriter.rewriteIfNeeded(trimmed, previous, profileJson);
        if (!effective.equals(trimmed)) {
            log.info("[Multi-Turn] 追问补全: '{}' -> '{}'", trimmed, effective);
        }
        return effective;
    }

    /**
     * 画像读取的静默降级：任何异常都按无画像处理，不影响改写主路径。
     */
    private String loadProfileQuietly(String userId) {
        try {
            return profileService.getProfile(userId);
        } catch (Exception e) {
            log.warn("[Multi-Turn] 画像读取异常，跳过注入: userId={}, error={}",
                    userId, e.getMessage());
            return null;
        }
    }

    /**
     * 仅更新 Redis 最新语义 DSL 快照（供 A2aSqlHitlRunner 在 SQL 子查询成功产出 DSL 时立即更新，不记轮次流水）。
     *
     * @param sessionId         会话 ID
     * @param rawQuestion       子问题原文
     * @param effectiveQuestion 追问补全后的生效子问题
     * @param dsl               本轮生成的语义 DSL
     */
    public void rememberSnapshotOnly(
            String sessionId, String rawQuestion, String effectiveQuestion,
            SemanticQueryDSL dsl) {
        if (sessionId == null || sessionId.isBlank() || dsl == null) {
            return;
        }
        store.putSnapshot(sessionId.trim(), SemanticSessionSnapshot.builder()
                .question(rawQuestion)
                .effectiveQuestion(effectiveQuestion)
                .dsl(dsl)
                .turnType("SQL")
                .timestamp(Instant.now())
                .build());
    }

    /**
     * 写侧：汇总完整一轮问答，有 DSL 时更新语义快照，并异步写穿 PG 归档表。
     *
     * @param sessionId         会话 ID
     * @param userId            归属用户 ID（可为 null，按 sessionId 派生）
     * @param rawQuestion       用户原始问句
     * @param effectiveQuestion 经补全后的问句
     * @param dsl               本轮语义 DSL（非 SQL 轮次可为 null，为 null 时不覆盖上一轮 snapshot）
     * @param answer            本轮自然语言回答结论（自动按 memory.answer-max-chars 截断）
     * @param turnType          轮次类型（SQL / SOP / MIXED / CHITCHAT）
     */
    public void rememberTurn(
            String sessionId, String userId,
            String rawQuestion, String effectiveQuestion,
            SemanticQueryDSL dsl, String answer, String turnType) {
        if (sessionId == null || sessionId.isBlank()
                || rawQuestion == null || rawQuestion.isBlank()) {
            return;
        }
        String sid = sessionId.trim();
        String resolvedUserId = userKeyResolver.resolve(sid, userId);
        String truncatedAnswer = truncateAnswer(answer);
        String resolvedTurnType = (turnType == null || turnType.isBlank())
                ? (dsl != null ? "SQL" : "CHITCHAT")
                : turnType;
        String effQ = (effectiveQuestion == null || effectiveQuestion.isBlank())
                ? rawQuestion
                : effectiveQuestion;

        SemanticSessionSnapshot turn = SemanticSessionSnapshot.builder()
                .question(rawQuestion)
                .effectiveQuestion(effQ)
                .dsl(dsl)
                .answer(truncatedAnswer)
                .turnType(resolvedTurnType)
                .timestamp(Instant.now())
                .build();

        if (dsl != null) {
            store.putSnapshot(sid, turn);
        }
        archiveWriter.writeAsync(
                sid, resolvedUserId, rawQuestion, effQ,
                dsl, truncatedAnswer, resolvedTurnType);
    }

    /**
     * 回答超长时保留结尾：流式回答常以"我来帮你查询一下…"之类开场白起头，结论在后面。
     */
    private String truncateAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String trimmed = answer.trim();
        int maxChars = memoryProperties.getAnswerMaxChars();
        if (maxChars > 0 && trimmed.length() > maxChars) {
            return trimmed.substring(trimmed.length() - maxChars);
        }
        return trimmed;
    }
}
