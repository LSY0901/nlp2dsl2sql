package org.example.nlp2dsl2sql.a2a.trace;

import com.alibaba.fastjson2.JSONObject;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatUsage;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 基于 v2 {@link MiddlewareBase} 的 JSONL 耗时 trace 中间件。
 * <p>
 * 在 {@code onAgent} 层观察 AgentEvent 流，只记录两类调用耗时，每行一次调用：
 * <ul>
 *   <li>{@code llm-call}：ModelCallStart → ModelCallEnd，含 token 用量（按 replyId 配对）</li>
 *   <li>{@code tool-call}：ToolCallStart/ToolResultStart → ToolResultEnd，
 *       含工具名与结果状态（按 toolCallId 配对，ToolResultStart 刷新起点以排除
 *       LLM 参数流式生成耗时）</li>
 * </ul>
 * 每行注入 sessionId 与 agentName（区分 host 编排层与 sql HITL 层），
 * 保证文件可按会话回放；其余事件（含 token 级 Delta）一律不落盘。
 * 线程安全，多 Agent / 多会话并发时按行原子写入。
 */
@Slf4j
public class JsonlTraceMiddleware implements MiddlewareBase, Closeable {

    private final BufferedWriter writer;
    private final boolean flushEveryLine;

    /**
     * @param file           目标 JSONL 文件
     * @param append         是否追加（false 则启动时截断）
     * @param flushEveryLine 是否每行落盘（false 走缓冲，close 时统一落盘）
     */
    public JsonlTraceMiddleware(Path file, boolean append, boolean flushEveryLine)
            throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        StandardOpenOption[] options = append
                ? new StandardOpenOption[]{
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND}
                : new StandardOpenOption[]{
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING};
        this.writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8, options);
        this.flushEveryLine = flushEveryLine;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String sessionId = context == null ? null : context.getSessionId();
        String agentName = agent == null ? null : agent.getName();
        CallTimingTracker tracker = new CallTimingTracker(sessionId, agentName);
        return next.apply(input)
                .doOnNext(tracker::onEvent)
                .doOnError(e -> recordError(e, sessionId, agentName));
    }

    /**
     * 单次 Agent 执行内的调用配对器（事件按顺序消费，无需跨线程同步）。
     */
    private class CallTimingTracker {

        private final String sessionId;
        private final String agentName;
        private final Map<String, Long> llmStartNanos = new HashMap<>();
        private final Map<String, Long> toolStartNanos = new HashMap<>();

        CallTimingTracker(String sessionId, String agentName) {
            this.sessionId = sessionId;
            this.agentName = agentName;
        }

        void onEvent(AgentEvent event) {
            if (event instanceof ModelCallStartEvent start) {
                llmStartNanos.put(replyKey(start.getReplyId()),
                        System.nanoTime());
                return;
            }
            if (event instanceof ModelCallEndEvent end) {
                finishLlm(end);
                return;
            }
            if (event instanceof ToolCallStartEvent start) {
                toolStartNanos.put(start.getToolCallId(), System.nanoTime());
                return;
            }
            if (event instanceof ToolResultStartEvent start) {
                if (toolStartNanos.containsKey(start.getToolCallId())) {
                    toolStartNanos.put(start.getToolCallId(),
                            System.nanoTime());
                }
                return;
            }
            if (event instanceof ToolResultEndEvent end) {
                finishTool(end);
            }
        }

        private void finishLlm(ModelCallEndEvent end) {
            Long startNanos = llmStartNanos.remove(replyKey(end.getReplyId()));
            if (startNanos == null) {
                return;
            }
            long durationMs = elapsedMs(startNanos);
            JSONObject node = baseNode("llm-call");
            putIfNotBlank(node, "replyId", end.getReplyId());
            node.put("durationMs", durationMs);
            ChatUsage usage = end.getUsage();
            if (usage != null) {
                node.put("inputTokens", usage.getInputTokens());
                node.put("outputTokens", usage.getOutputTokens());
                node.put("totalTokens", usage.getTotalTokens());
            }
            appendQuietly(node);
        }

        private void finishTool(ToolResultEndEvent end) {
            Long startNanos = toolStartNanos.remove(end.getToolCallId());
            if (startNanos == null) {
                return;
            }
            JSONObject node = baseNode("tool-call");
            putIfNotBlank(node, "name", end.getToolCallName());
            putIfNotBlank(node, "toolCallId", end.getToolCallId());
            putIfNotBlank(node, "state",
                    end.getState() == null ? null : end.getState().name());
            node.put("durationMs", elapsedMs(startNanos));
            appendQuietly(node);
        }

        private JSONObject baseNode(String type) {
            JSONObject node = new JSONObject();
            node.put("time", System.currentTimeMillis());
            node.put("type", type);
            putIfNotBlank(node, "sessionId", sessionId);
            putIfNotBlank(node, "agentName", agentName);
            return node;
        }

        private long elapsedMs(long startNanos) {
            return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
        }

        private String replyKey(String replyId) {
            return replyId == null ? "" : replyId;
        }
    }

    /**
     * Agent 执行异常时记录错误行（不中断执行流）。
     */
    private void recordError(Throwable error, String sessionId, String agentName) {
        try {
            JSONObject node = new JSONObject();
            node.put("time", System.currentTimeMillis());
            node.put("type", "ERROR");
            if (error != null && error.getMessage() != null) {
                node.put("error", error.getMessage());
            }
            putIfNotBlank(node, "sessionId", sessionId);
            putIfNotBlank(node, "agentName", agentName);
            append(node);
        } catch (IOException ignored) {
            log.warn("[Trace] append failed, file may be closed");
        }
    }

    private void putIfNotBlank(JSONObject node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.put(key, value);
        }
    }

    private void appendQuietly(JSONObject node) {
        try {
            append(node);
        } catch (IOException e) {
            log.warn("[Trace] append failed: {}", e.getMessage());
        }
    }

    private void append(JSONObject node) throws IOException {
        String line = node.toJSONString();
        synchronized (writer) {
            writer.write(line);
            writer.newLine();
            if (flushEveryLine) {
                writer.flush();
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (writer) {
            writer.close();
        }
    }
}
