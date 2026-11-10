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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 会话摘要定时任务：扫描"已结束且未摘要"的会话（末轮闲置超阈值），
 * 从 PG 流水读取全量轮次，用 fast 档 LLM 压缩成一条摘要并 upsert。
 * <p>
 * 单个会话失败仅告警，不影响同批其它会话；幂等（session_id UNIQUE 覆盖式）。
 */
@Slf4j
@Component
public class MemorySummaryJob {

    private final MemorySessionArchiveMapper archiveMapper;
    private final MemorySessionSummaryMapper summaryMapper;
    private final A2aHostModelRouter modelRouter;
    private final MemoryProperties memoryProperties;

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
                summarizeOne(candidate);
            } catch (Exception e) {
                log.warn("[MemorySummary] 单会话摘要失败: sessionId={}, error={}",
                        candidate.get("sessionId"), e.getMessage());
            }
        }
    }

    /**
     * 压缩单个会话：读流水 → LLM 摘要 → upsert。
     */
    private void summarizeOne(Map<String, Object> candidate) {
        String sessionId = String.valueOf(candidate.get("sessionId"));
        Object userIdObj = candidate.get("userId");
        String userId = userIdObj == null ? null : String.valueOf(userIdObj);

        List<MemorySessionArchive> turns = archiveMapper.selectList(
                new LambdaQueryWrapper<MemorySessionArchive>()
                        .eq(MemorySessionArchive::getSessionId, sessionId)
                        .orderByAsc(MemorySessionArchive::getCreatedAt));
        if (turns == null || turns.isEmpty()) {
            return;
        }

        String summary = callFastLlmSummary(buildTurnsText(turns));
        if (summary == null || summary.isBlank()) {
            log.warn("[MemorySummary] LLM 摘要失败，本轮跳过: sessionId={}", sessionId);
            return;
        }

        MemorySessionSummary record = new MemorySessionSummary();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setSummary(summary.trim());
        record.setTurnCount(turns.size());
        record.setFirstTurnAt(turns.get(0).getCreatedAt());
        record.setLastTurnAt(turns.get(turns.size() - 1).getCreatedAt());
        summaryMapper.upsert(record);
        log.info("[MemorySummary] 会话摘要完成 sessionId={}, turns={}",
                sessionId, turns.size());
    }

    /**
     * 拼会话轮次文本（压缩原料）。
     */
    private String buildTurnsText(List<MemorySessionArchive> turns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < turns.size(); i++) {
            MemorySessionArchive turn = turns.get(i);
            sb.append(i + 1).append(". 问:").append(turn.getQuestion());
            if (turn.getEffectiveQuestion() != null
                    && !turn.getEffectiveQuestion().equals(turn.getQuestion())) {
                sb.append(" 改写:").append(turn.getEffectiveQuestion());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * fast 档 LLM 摘要；失败返回 null。测试子类覆写以脱离模型。
     */
    protected String callFastLlmSummary(String turnsText) {
        String prompt = """
                你是数据查询会话压缩器。把下面的多轮问数会话压缩成 100 字以内的中文摘要。
                保留：用户问了什么（指标/维度/过滤条件如何演进）、关键结论。
                不要 SQL、不要工具过程、不要逐轮复述。
                【会话轮次】
                %s
                【输出】直接输出摘要正文，禁止任何前缀、解释或引号。
                """.formatted(turnsText);
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
