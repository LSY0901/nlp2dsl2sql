package org.example.nlp2dsl2sql.a2a.trace;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.message.ToolResultState;

import java.util.HashMap;
import java.util.Map;

/**
 * 按 AgentEvent 流跟踪工具调用（工具名 + 状态 + 耗时），完成时写入
 * {@link HostTraceRecorder}。
 * <p>
 * 时序规则：
 * <ul>
 *   <li>{@link ToolCallStartEvent} — 建立条目，startTime=now（兜底起点）</li>
 *   <li>{@link ToolResultStartEvent} — 刷新 startTime=now（真实工具执行起点，
 *       排除 LLM 参数流式生成耗时）</li>
 *   <li>{@link ToolResultEndEvent} — 落状态与耗时，写入 recorder，出栈</li>
 * </ul>
 * 审批后经 {@code agent.call()} 执行的工具（execute_sql）事件不外露，
 * 由调用方用 {@link #complete(String, String, long)} 手动终结对应条目。
 * <p>
 * 单会话单次执行使用一个实例（事件按顺序消费），无需跨线程同步。
 */
public class AgentEventToolTracer {

    private final HostTraceRecorder recorder;
    private final String sessionId;
    private final String layer;
    private final Map<String, HostTraceRecord.ToolCallRecord> inflight =
            new HashMap<>();

    /**
     * @param recorder  trace 记录器
     * @param sessionId 会话 ID
     * @param layer     层标识（host / sql）
     */
    public AgentEventToolTracer(
            HostTraceRecorder recorder, String sessionId, String layer) {
        this.recorder = recorder;
        this.sessionId = sessionId;
        this.layer = layer;
    }

    /**
     * 处理单个 Agent 事件。
     *
     * @param event AgentScope 事件
     */
    public void onEvent(AgentEvent event) {
        if (event instanceof ToolCallStartEvent start) {
            HostTraceRecord.ToolCallRecord record =
                    new HostTraceRecord.ToolCallRecord();
            record.setLayer(layer);
            record.setName(start.getToolCallName());
            record.setToolCallId(start.getToolCallId());
            record.setState(ToolResultState.RUNNING.name());
            record.setStartTimeMs(System.currentTimeMillis());
            inflight.put(start.getToolCallId(), record);
            return;
        }
        if (event instanceof ToolResultStartEvent start) {
            HostTraceRecord.ToolCallRecord record =
                    inflight.get(start.getToolCallId());
            if (record != null) {
                record.setStartTimeMs(System.currentTimeMillis());
            }
            return;
        }
        if (event instanceof ToolResultEndEvent end) {
            HostTraceRecord.ToolCallRecord record =
                    inflight.remove(end.getToolCallId());
            if (record == null) {
                return;
            }
            finish(record, end.getState());
        }
    }

    /**
     * 手动终结栈内条目（用于 {@code agent.call()} 内执行、事件不外露的工具，
     * 如审批后的 execute_sql）。条目不存在时按传入 name 新建。
     *
     * @param toolCallId 工具调用 ID
     * @param name       工具名（仅在条目不存在时用于新建）
     * @param state      结果状态（SUCCESS / ERROR / DENIED ...）
     * @param durationMs 实测耗时毫秒
     */
    public void complete(
            String toolCallId, String name, String state, long durationMs) {
        HostTraceRecord.ToolCallRecord record = inflight.remove(toolCallId);
        if (record == null) {
            record = new HostTraceRecord.ToolCallRecord();
            record.setLayer(layer);
            record.setName(name);
            record.setToolCallId(toolCallId);
            record.setStartTimeMs(
                    Math.max(0, System.currentTimeMillis() - durationMs));
        }
        record.setState(state);
        record.setDurationMs(Math.max(0, durationMs));
        recorder.tool(sessionId, record);
    }

    /**
     * 丢弃栈内条目（不落盘）。
     *
     * @param toolCallId 工具调用 ID
     */
    public void discard(String toolCallId) {
        inflight.remove(toolCallId);
    }

    /**
     * 执行流结束：将残留 RUNNING 条目以 INTERRUPTED 落盘。
     */
    public void endAll() {
        long now = System.currentTimeMillis();
        for (HostTraceRecord.ToolCallRecord record : inflight.values()) {
            record.setState(ToolResultState.INTERRUPTED.name());
            record.setDurationMs(Math.max(0, now - record.getStartTimeMs()));
            recorder.tool(sessionId, record);
        }
        inflight.clear();
    }

    private void finish(
            HostTraceRecord.ToolCallRecord record, ToolResultState state) {
        record.setState(state.name());
        record.setDurationMs(Math.max(
                0, System.currentTimeMillis() - record.getStartTimeMs()));
        recorder.tool(sessionId, record);
    }
}
