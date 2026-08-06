package org.example.nlp2dsl2sql.a2a.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;

/**
 * 基于 v2 {@link MiddlewareBase} 的 JSONL trace 落盘中间件。
 * <p>
 * 替代已废弃（since 2.0.0，forRemoval）的 {@code JsonlTraceExporter}：在 {@code onAgent}
 * 层观察完整 AgentEvent 流，逐条序列化为 JSON 追加写入文件。线程安全，多 Agent / 多会话
 * 并发时按行原子写入。
 */
@Slf4j
public class JsonlTraceMiddleware implements MiddlewareBase, Closeable {

    private final BufferedWriter writer;
    private final boolean flushEveryLine;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param file           目标 JSONL 文件
     * @param append         是否追加（false 则启动时截断）
     * @param flushEveryLine 是否每行落盘
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
        return next.apply(input)
                .doOnNext(this::record)
                .doOnError(e -> recordError(e));
    }

    /**
     * 序列化单个事件为 JSON 行写入。
     */
    private void record(AgentEvent event) {
        try {
            ObjectNode node = mapper.valueToTree(event);
            node.put("eventClass", event.getClass().getSimpleName());
            append(node);
        } catch (Exception e) {
            log.warn("[Trace] record event failed: {}", e.getMessage());
            recordFallback("ERROR", event.getClass().getSimpleName(), null);
        }
    }

    /**
     * Agent 执行异常时记录错误行（不中断执行流）。
     */
    private void recordError(Throwable error) {
        recordFallback("ERROR", null,
                error == null ? null : error.getMessage());
    }

    private void recordFallback(String type, String eventClass, String error) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("time", System.currentTimeMillis());
            node.put("type", type);
            if (eventClass != null) {
                node.put("eventClass", eventClass);
            }
            if (error != null) {
                node.put("error", error);
            }
            append(node);
        } catch (IOException ignored) {
            log.warn("[Trace] append failed, file may be closed");
        }
    }

    private void append(JsonNode node) throws IOException {
        String line = mapper.writeValueAsString(node);
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
        writer.close();
    }
}
