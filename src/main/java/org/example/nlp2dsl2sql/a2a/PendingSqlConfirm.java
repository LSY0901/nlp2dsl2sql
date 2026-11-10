package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 一次 execute_sql ASK 挂起快照。
 */
@Getter
public class PendingSqlConfirm {

    private final String sessionId;
    private final String confirmId;
    private final ReActAgent agent;
    private final List<ToolUseBlock> toolCalls;
    private final CompletableFuture<Boolean> decision;
    private final long createdAtMs;

    /**
     * 构造挂起快照。
     *
     * @param sessionId 会话 ID
     * @param confirmId 确认 ID（待确认卡片中的 confirmId；为空时按会话单槽退化）
     * @param agent     可恢复的 HITL Agent
     * @param toolCalls 待确认工具调用
     */
    public PendingSqlConfirm(
            String sessionId,
            String confirmId,
            ReActAgent agent,
            List<ToolUseBlock> toolCalls) {
        this.sessionId = sessionId;
        this.confirmId = confirmId;
        this.agent = agent;
        this.toolCalls = toolCalls;
        this.decision = new CompletableFuture<>();
        this.createdAtMs = System.currentTimeMillis();
    }
}
