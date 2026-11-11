package org.example.nlp2dsl2sql.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.a2a.A2aHostModelRouter;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionArchiveMapper;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionSummaryMapper;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionArchive;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionSummary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话摘要任务（双触发）：
 * <ol>
 *   <li>定时扫描：末轮闲置超阈值且存在新增轮次的会话（{@link #summarizeIdleSessions()}）；</li>
 *   <li>窗口溢出即时异步触发：Redis {@code AgentState} 发生滑动窗口截断时立即压缩（{@link #summarizeSessionAsync}）。</li>
 * </ol>
 * 增量压缩：只读取上次摘要之后的新增轮次（含回答结论与轮次类型），与已有摘要一起用 fast 档 LLM 合并后覆盖式 upsert。
 * 单个会话失败仅告警，不影响同批其它会话；幂等（session_id UNIQUE 覆盖式）。
 */
@Slf4j
@Component
public class MemorySummaryJob {

    private final MemorySessionArchiveMapper archiveMapper;
    private final MemorySessionSummaryMapper summaryMapper;
    private final A2aHostModelRouter modelRouter;
    private final MemoryProperties memoryProperties;

    /** 单会话并发摘要防重集合 */
    private final Set<String> summarizingSessions = ConcurrentHashMap.newKeySet();

    /**
     * @param archiveMapper    流水 Mapper
     * @param summaryMapper    摘要 Mapper
     * @param modelRouter      模型路由（fast 档）
     * @param memoryProperties 记忆配置
     */
    public MemorySummaryJob(
            MemorySessionArchiveMapper archiveMapper,
            MemorySessionSummaryMapper summaryMapper,
            A2aHostModelRouter modelRouter,
            MemoryProperties memoryProperties) {
        this.archiveMapper = archiveMapper;
        this.summaryMapper = summaryMapper;
        this.modelRouter = modelRouter;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 周期扫描并压缩闲置会话。
     */
    @Scheduled(fixedDelayString =
            "${memory.longterm.summary.scan-fixed-delay-ms:600000}")
    public void summarizeIdleSessions() {
        if (!memoryProperties.getLongterm().isJobsEnabled()) {
            return;
        }
        int idleMinutes = memoryProperties.getLongterm().getSummary().getIdleMinutes();
        List<Map<String, Object>> candidates;
        try {
            candidates = archiveMapper.findFinishedSessions(idleMinutes);
        } catch (Exception e) {
            log.warn("[MemorySummary] 候选查询失败: {}", e.getMessage());
            return;
        }
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        log.info("━━━ [MemorySummary] 待摘要会话 {} 个（idle>{}min）━━━",
                candidates.size(), idleMinutes);
        for (Map<String, Object> candidate : candidates) {
            try {
                summarizeOne(candidate, 1);
            } catch (Exception e) {
                log.warn("[MemorySummary] 单会话摘要失败: sessionId={}, error={}",
                        candidate.get("sessionId"), e.getMessage());
            }
        }
    }

    /**
     * 短期 Redis AgentState 滑动窗口截断溢出时，异步增量压缩该会话已落库的新增轮次。
     * <p>
     * 节流：新增轮次少于 {@code memory.longterm.summary.evict-min-new-turns} 时直接跳过（只查一次 PG，不调 LLM），
     * 避免窗口稳态下"每轮截断即每轮摘要"。本轮问答在流结束后才异步落库，因此不会计入本次触发，
     * 会在下一次触发或闲置扫描中被合并。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID（可为 null，为 null 时从流水记录回填）
     */
    @Async("memoryArchiveExecutor")
    public void summarizeSessionAsync(String sessionId, String userId) {
        if (!memoryProperties.getLongterm().isJobsEnabled()) {
            return;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String sid = sessionId.trim();
        if (!summarizingSessions.add(sid)) {
            log.debug("[MemorySummary] 会话正在摘要中，跳过重复触发: sessionId={}", sid);
            return;
        }
        try {
            Map<String, Object> candidate = new HashMap<>();
            candidate.put("sessionId", sid);
            candidate.put("userId", userId);
            summarizeOne(candidate,
                    memoryProperties.getLongterm().getSummary().getEvictMinNewTurns());
        } catch (Exception e) {
            log.warn("[MemorySummary] 窗口溢出异步摘要失败: sessionId={}, error={}",
                    sid, e.getMessage());
        } finally {
            summarizingSessions.remove(sid);
        }
    }

    /**
     * 增量压缩单个会话：只读取上次摘要 {@code last_turn_at} 之后的新增轮次，
     * 与已有摘要一起交给 LLM 合并，单次成本只与新增轮次数相关（避免会话越长越贵）。
     *
     * @param candidate   候选（sessionId / userId）
     * @param minNewTurns 新增轮次下限，不足则跳过（不调 LLM）
     */
    private void summarizeOne(Map<String, Object> candidate, int minNewTurns) {
        String sessionId = String.valueOf(candidate.get("sessionId"));
        Object userIdObj = candidate.get("userId");
        String userId = userIdObj == null ? null : String.valueOf(userIdObj);

        MemorySessionSummary previous = summaryMapper.selectOne(
                new LambdaQueryWrapper<MemorySessionSummary>()
                        .eq(MemorySessionSummary::getSessionId, sessionId));
        LambdaQueryWrapper<MemorySessionArchive> query =
                new LambdaQueryWrapper<MemorySessionArchive>()
                        .eq(MemorySessionArchive::getSessionId, sessionId);
        if (previous != null && previous.getLastTurnAt() != null) {
            query.gt(MemorySessionArchive::getCreatedAt, previous.getLastTurnAt());
        }
        query.orderByAsc(MemorySessionArchive::getCreatedAt);

        List<MemorySessionArchive> newTurns = archiveMapper.selectList(query);
        if (newTurns == null || newTurns.size() < Math.max(1, minNewTurns)) {
            log.debug("[MemorySummary] 新增轮次不足，跳过: sessionId={}, newTurns={}, min={}",
                    sessionId, newTurns == null ? 0 : newTurns.size(), minNewTurns);
            return;
        }
        if (userId == null || userId.isBlank() || "null".equals(userId)) {
            userId = newTurns.get(newTurns.size() - 1).getUserId();
        }

        String previousSummary = previous == null ? null : previous.getSummary();
        String summary = callFastLlmSummary(previousSummary, buildTurnsText(newTurns));
        if (summary == null || summary.isBlank()) {
            log.warn("[MemorySummary] LLM 摘要失败，本轮跳过: sessionId={}", sessionId);
            return;
        }

        int previousCount = (previous == null || previous.getTurnCount() == null)
                ? 0 : previous.getTurnCount();
        MemorySessionSummary record = new MemorySessionSummary();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setSummary(summary.trim());
        record.setTurnCount(previousCount + newTurns.size());
        record.setFirstTurnAt(previous != null && previous.getFirstTurnAt() != null
                ? previous.getFirstTurnAt()
                : newTurns.get(0).getCreatedAt());
        record.setLastTurnAt(newTurns.get(newTurns.size() - 1).getCreatedAt());
        summaryMapper.upsert(record);
        log.info("[MemorySummary] 会话摘要完成 sessionId={}, newTurns={}, totalTurns={}",
                sessionId, newTurns.size(), record.getTurnCount());
    }

    /**
     * 拼会话轮次文本（压缩原料，包含轮次类型、原始问题、改写问题、回答结论）。
     */
    String buildTurnsText(List<MemorySessionArchive> turns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < turns.size(); i++) {
            MemorySessionArchive turn = turns.get(i);
            String turnType = (turn.getTurnType() == null || turn.getTurnType().isBlank())
                    ? "SQL" : turn.getTurnType();
            sb.append(i + 1)
                    .append(". [类型:").append(turnType).append("] ")
                    .append("问:").append(turn.getQuestion());
            if (turn.getEffectiveQuestion() != null
                    && !turn.getEffectiveQuestion().equals(turn.getQuestion())) {
                sb.append(" 改写:").append(turn.getEffectiveQuestion());
            }
            if (turn.getAnswer() != null && !turn.getAnswer().isBlank()) {
                sb.append(" 答:").append(turn.getAnswer());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * fast 档 LLM 增量摘要；失败返回 null。测试子类覆写以脱离模型。
     *
     * @param previousSummary 已有摘要（首次为 null）
     * @param turnsText       新增轮次文本
     */
    protected String callFastLlmSummary(String previousSummary, String turnsText) {
        String previousBlock = (previousSummary == null || previousSummary.isBlank())
                ? "（无）" : previousSummary;
        String prompt = """
                你是数据查询与知识问答会话压缩器。把【已有摘要】与【新增轮次】合并成 100 字以内的中文摘要。
                保留：用户问了什么（指标/维度/过滤条件/规范SOP如何演进）、关键回答结论。
                不要 SQL、不要工具过程、不要逐轮复述。
                【已有摘要】
                %s
                【新增轮次】
                %s
                【输出】直接输出摘要正文，禁止任何前缀、解释或引号。
                """.formatted(previousBlock, turnsText);
        OpenAIChatModel fastModel = modelRouter.resolveTier("fast");
        StringBuilder sb = new StringBuilder();
        try {
            fastModel.stream(List.of(new UserMessage(prompt)), List.of(), null)
                    .doOnNext(resp -> {
                        if (resp.getContent() != null) {
                            for (var block : resp.getContent()) {
                                if (block instanceof TextBlock tb) {
                                    sb.append(tb.getText());
                                }
                            }
                        }
                    })
                    .blockLast(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.warn("[MemorySummary] LLM 调用失败: {}", e.getMessage());
            return null;
        }
        String result = sb.toString().trim();
        if (result.startsWith("\"") && result.endsWith("\"") && result.length() > 2) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result.isEmpty() ? null : result;
    }
}
