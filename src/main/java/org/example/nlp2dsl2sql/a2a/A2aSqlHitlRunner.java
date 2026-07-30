package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.dto.dsl.AgentSessionContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A2A Host 本地 SQL HITL 执行器：streamEvents + Permission 确认 + 恢复。
 * <p>
 * 用户拒绝后：应用 ConfirmResult(false)、自动拒绝后续 ASK，不再弹出确认 UI，
 * 并立即结束本子查询。
 */
@Slf4j
@Component
public class A2aSqlHitlRunner {

    /** 拒绝后自动排空后续 ASK 的最大次数，防止死循环 */
    private static final int MAX_AUTO_DENY_DEPTH = 3;

    private final SqlQueryHitlAgentFactory hitlFactory;
    private final A2aSqlConfirmRegistry confirmRegistry;

    /**
     * @param hitlFactory      HITL Agent 工厂
     * @param confirmRegistry  挂起表
     */
    public A2aSqlHitlRunner(
            SqlQueryHitlAgentFactory hitlFactory,
            A2aSqlConfirmRegistry confirmRegistry) {
        this.hitlFactory = hitlFactory;
        this.confirmRegistry = confirmRegistry;
    }

    /**
     * 运行带 SQL 确认的本地问数 Agent，过程增量写入 hostCtx。
     *
     * @param query     子问题
     * @param hostCtx   Host SSE 上下文
     * @param timeoutMs LLM/工具阶段超时（不含人工确认等待）
     * @return 最终文本（供 Host tool result）
     */
    public String run(
            String query, A2aHostChatContext hostCtx, long timeoutMs) {
        ReActAgent agent = hitlFactory.create();
        AgentSessionContext sqlSession = new AgentSessionContext();
        RuntimeContext sqlCtx = RuntimeContext.builder()
                .userId("lsy")
                .sessionId(hostCtx.getSessionId())
                .put(AgentSessionContext.class, sqlSession)
                .build();

        StringBuilder finalText = new StringBuilder();
        Duration phaseTimeout = Duration.ofMillis(Math.max(timeoutMs, 60_000L));
        AtomicBoolean userDenied = new AtomicBoolean(false);

        try {
            for (AgentEvent event : agent
                    .streamEvents(new UserMessage(query.trim()), sqlCtx)
                    .toIterable()) {
                if (event instanceof RequireUserConfirmEvent confirm) {
                    if (userDenied.get()) {
                        autoDenyAsking(
                                confirm.getToolCalls(), agent, sqlCtx,
                                phaseTimeout);
                        continue;
                    }
                    boolean approved = resumeAfterConfirm(
                            confirm, hostCtx, agent, sqlCtx,
                            finalText, phaseTimeout, 0);
                    if (!approved) {
                        userDenied.set(true);
                        break;
                    }
                    continue;
                }
                if (userDenied.get()) {
                    continue;
                }
                appendAndEmit(event, hostCtx, finalText);
            }
        } catch (Exception e) {
            log.warn("[HITL] SQL Agent 流式执行失败: {}", e.getMessage());
            return "SQL Agent 调用失败: " + e.getMessage();
        }

        if (finalText.length() == 0) {
            return "SQL Agent 返回空内容";
        }
        return finalText.toString();
    }

    /**
     * 处理 Permission ASK：推送 SQL、等待确认、ConfirmResult 恢复。
     *
     * @param depth 嵌套确认深度，防止死循环
     * @return 用户是否批准；拒绝则 false
     */
    private boolean resumeAfterConfirm(
            RequireUserConfirmEvent confirm,
            A2aHostChatContext hostCtx,
            ReActAgent agent,
            RuntimeContext sqlCtx,
            StringBuilder finalText,
            Duration phaseTimeout,
            int depth) {
        if (depth >= 3) {
            hostCtx.emit(A2aSqlConfirmTexts.formatResult(
                    false, "确认次数超限"));
            finishDenied(
                    agent, sqlCtx, confirm.getToolCalls(),
                    phaseTimeout, hostCtx, finalText);
            return false;
        }
        List<ToolUseBlock> toolCalls = confirm.getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            hostCtx.emit(A2aSqlConfirmTexts.formatResult(false, "无待确认工具"));
            return false;
        }

        String sql = extractSql(toolCalls);
        String toolCallId = toolCalls.get(0).getId();
        hostCtx.emit(A2aSqlConfirmTexts.formatPending(
                hostCtx.getSessionId(), toolCallId, sql));

        PendingSqlConfirm pending = new PendingSqlConfirm(
                hostCtx.getSessionId(), agent, toolCalls);
        confirmRegistry.put(pending);

        boolean approved = waitDecision(pending, hostCtx);
        confirmRegistry.remove(hostCtx.getSessionId());

        if (!approved) {
            finishDenied(
                    agent, sqlCtx, toolCalls,
                    phaseTimeout, hostCtx, finalText);
            return false;
        }

        Msg resumeMsg = buildResumeMsg(true, toolCalls);
        try {
            Msg result = agent.call(List.of(resumeMsg), sqlCtx)
                    .block(phaseTimeout);
            return handleApprovedResumeResult(
                    result, hostCtx, agent, sqlCtx,
                    finalText, phaseTimeout, depth);
        } catch (Exception e) {
            log.warn("[HITL] 恢复执行失败: {}", e.getMessage());
            String err = "SQL 确认后恢复失败: " + e.getMessage();
            hostCtx.emit(err);
            finalText.append(err);
            return true;
        }
    }

    /**
     * 用户拒绝：应用 ConfirmResult(false)，自动拒绝后续 ASK，结束子查询。
     */
    private void finishDenied(
            ReActAgent agent,
            RuntimeContext sqlCtx,
            List<ToolUseBlock> toolCalls,
            Duration phaseTimeout,
            A2aHostChatContext hostCtx,
            StringBuilder finalText) {
        try {
            Msg result = agent.call(
                    List.of(buildResumeMsg(false, toolCalls)), sqlCtx)
                    .block(phaseTimeout);
            int depth = 0;
            while (result != null
                    && result.getGenerateReason()
                    == GenerateReason.PERMISSION_ASKING
                    && depth < MAX_AUTO_DENY_DEPTH) {
                List<ToolUseBlock> asking = result.getContentBlocks(
                        ToolUseBlock.class);
                if (asking == null || asking.isEmpty()) {
                    break;
                }
                log.info("[HITL] 拒绝后自动拒绝后续 ASK depth={} sessionId={}",
                        depth, hostCtx.getSessionId());
                result = agent.call(
                        List.of(buildResumeMsg(false, asking)), sqlCtx)
                        .block(phaseTimeout);
                depth++;
            }
        } catch (Exception e) {
            log.warn("[HITL] 拒绝后恢复/排空失败: {}", e.getMessage());
        }
        String cancel = A2aSqlConfirmTexts.cancelledByUser();
        hostCtx.emit(cancel);
        finalText.setLength(0);
        finalText.append(cancel);
    }

    /**
     * 用户已拒绝后，对流中再次出现的 ASK 静默拒绝（不弹 UI）。
     */
    private void autoDenyAsking(
            List<ToolUseBlock> toolCalls,
            ReActAgent agent,
            RuntimeContext sqlCtx,
            Duration phaseTimeout) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        try {
            agent.call(List.of(buildResumeMsg(false, toolCalls)), sqlCtx)
                    .block(phaseTimeout);
        } catch (Exception e) {
            log.warn("[HITL] 静默拒绝后续 ASK 失败: {}", e.getMessage());
        }
    }

    /**
     * 阻塞等待用户决策；超时视为拒绝。
     */
    private boolean waitDecision(
            PendingSqlConfirm pending, A2aHostChatContext hostCtx) {
        try {
            boolean approved = pending.getDecision().get(
                    A2aSqlConfirmRegistry.TIMEOUT_MS, TimeUnit.MILLISECONDS);
            hostCtx.emit(A2aSqlConfirmTexts.formatResult(
                    approved, approved ? null : "user denied"));
            return approved;
        } catch (TimeoutException e) {
            pending.getDecision().complete(false);
            hostCtx.emit(A2aSqlConfirmTexts.formatResult(false, "timeout"));
            return false;
        } catch (Exception e) {
            pending.getDecision().complete(false);
            hostCtx.emit(A2aSqlConfirmTexts.formatResult(
                    false, e.getMessage()));
            return false;
        }
    }

    /**
     * 处理批准后的恢复结果；若再次 ASKING 则继续向用户确认（最多 3 次）。
     *
     * @return 嵌套确认是否仍为批准；用户拒绝则为 false
     */
    private boolean handleApprovedResumeResult(
            Msg result,
            A2aHostChatContext hostCtx,
            ReActAgent agent,
            RuntimeContext sqlCtx,
            StringBuilder finalText,
            Duration phaseTimeout,
            int depth) {
        if (result == null) {
            return true;
        }
        if (result.getGenerateReason() == GenerateReason.PERMISSION_ASKING
                && depth < 3) {
            List<ToolUseBlock> asking = result.getContentBlocks(
                    ToolUseBlock.class);
            if (asking != null && !asking.isEmpty()) {
                RequireUserConfirmEvent again =
                        new RequireUserConfirmEvent(
                                result.getId() != null
                                        ? result.getId() : "resume",
                                asking);
                return resumeAfterConfirm(
                        again, hostCtx, agent, sqlCtx,
                        finalText, phaseTimeout, depth + 1);
            }
        }
        String text = A2aMsgTexts.extract(result);
        if (!text.isBlank()) {
            hostCtx.emit(text);
            finalText.append(text);
        }
        return true;
    }

    /**
     * 构造带 ConfirmResult 的恢复消息。
     */
    private Msg buildResumeMsg(boolean approved, List<ToolUseBlock> toolCalls) {
        List<ConfirmResult> confirmResults = toolCalls.stream()
                .map(t -> new ConfirmResult(approved, t))
                .toList();
        Map<String, Object> meta = new HashMap<>();
        meta.put(Msg.METADATA_CONFIRM_RESULTS, confirmResults);
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(approved ? "approved" : "denied")
                .metadata(meta)
                .build();
    }

    /**
     * 从待确认工具入参中提取 SQL。
     */
    private String extractSql(List<ToolUseBlock> toolCalls) {
        for (ToolUseBlock block : toolCalls) {
            if (block == null || block.getInput() == null) {
                continue;
            }
            Object sql = block.getInput().get("sql");
            if (sql != null) {
                return String.valueOf(sql);
            }
        }
        return "(未解析到 SQL)";
    }

    /**
     * 将普通流式事件映射为 SSE 文本并累积。
     */
    private void appendAndEmit(
            AgentEvent event,
            A2aHostChatContext hostCtx,
            StringBuilder finalText) {
        if (event instanceof TextBlockDeltaEvent delta) {
            String text = delta.getDelta();
            if (text != null && !text.isEmpty()) {
                hostCtx.emit(text);
                finalText.append(text);
            }
            return;
        }
        if (event instanceof ToolCallStartEvent toolCall) {
            String name = toolCall.getToolCallName();
            String chunk = "\n\n========== [SQL工具开始] "
                    + name + " ==========\n";
            hostCtx.emit(chunk);
            return;
        }
        if (event instanceof ToolResultTextDeltaEvent toolResult) {
            String text = toolResult.getDelta();
            if (text != null && !text.isEmpty()) {
                hostCtx.emit(text);
            }
            return;
        }
        if (event instanceof ToolResultEndEvent toolEnd) {
            String chunk = "\n========== [SQL工具结束] "
                    + toolEnd.getToolCallName()
                    + " (" + toolEnd.getState() + ") ==========\n\n";
            hostCtx.emit(chunk);
        }
    }
}
