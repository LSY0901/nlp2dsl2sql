package org.example.nlp2dsl2sql.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 会话多轮记忆的唯一入口：读侧做追问消歧，写侧沉淀语义快照。
 * <p>
 * 五种编排形态（V2 固定管线 / ReAct / Skill / Planner / A2A Host）共用同一份
 * 快照，因为它们共享同一套 DSL 生成工具与提示词，产物是模式无关的。
 * <p>
 * 与 AgentScope 内置 memory 的分工：本类只负责确定性的追问改写；
 * 用户画像、能力边界这类开放式记忆仍由 HarnessAgent 自行 flush 到 MEMORY.md。
 */
@Slf4j
@Component
public class QueryPreprocessor {

    private final SemanticSessionStore store;
    private final QueryContextRewriter rewriter;

    public QueryPreprocessor(SemanticSessionStore store, QueryContextRewriter rewriter) {
        this.store = store;
        this.rewriter = rewriter;
    }

    /**
     * 读侧：把"那四年级呢"这类依赖上下文的追问补全为自包含问句。
     *
     * @param sessionId 会话 ID；为空表示无记忆可用，直接透传原问句
     * @param question  用户原始问句
     * @return 生效问句（命中追问特征且有上一轮快照时才可能变化）
     */
    public String preprocess(String sessionId, String question) {
        if (question == null || question.isBlank()) {
            return question;
        }
        String trimmed = question.trim();
        if (sessionId == null || sessionId.isBlank()) {
            return trimmed;
        }
        String effective = rewriter.rewriteIfNeeded(trimmed, store.get(sessionId));
        if (!effective.equals(trimmed)) {
            log.info("[Multi-Turn] 追问补全: '{}' -> '{}'", trimmed, effective);
        }
        return effective;
    }

    /**
     * 写侧：沉淀本轮语义快照。
     * <p>
     * dsl 为 null 表示本轮没走到 DSL 生成（意图非业务、检索无候选、Agent 提前
     * 回答、用户中途放弃），此时没有可继承的语义槽位，不覆盖上一轮快照。
     *
     * @param sessionId       会话 ID
     * @param rawQuestion     用户原始问句
     * @param effectiveQuestion 经 {@link #preprocess} 补全后的问句
     * @param dsl             本轮语义 DSL，可为 null
     */
    public void remember(String sessionId, String rawQuestion, String effectiveQuestion,
                         SemanticQueryDSL dsl) {
        if (sessionId == null || sessionId.isBlank() || dsl == null) {
            return;
        }
        store.put(sessionId, SemanticSessionSnapshot.builder()
                .question(rawQuestion)
                .effectiveQuestion(effectiveQuestion)
                .dsl(dsl)
                .timestamp(Instant.now())
                .build());
    }
}
