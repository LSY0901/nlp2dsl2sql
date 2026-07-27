package org.example.nlp2dsl2sql.service.impl;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.service.IA2aHostService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * A2A Host SSE 服务。
 * <p>
 * 通过 {@link HarnessAgent#streamEvents} 真流式输出到前端：
 * LLM token 增量、A2A 远程工具调用进度与返回内容。
 */
@Slf4j
@Service
public class A2aHostServiceImpl implements IA2aHostService {

    private final HarnessAgent a2aHostAgent;

    /**
     * 构造 A2A Host 服务。
     *
     * @param a2aHostAgent Host Agent
     */
    public A2aHostServiceImpl(
            @Qualifier("a2aHostAgent") HarnessAgent a2aHostAgent) {
        this.a2aHostAgent = a2aHostAgent;
    }

    /**
     * 启动 Host Agent，流式返回 SSE 文本。
     * <p>
     * 订阅后立即启动 ReAct 循环；LLM token 与 A2A 工具事件
     * 边生成边推送，全程不阻塞等待整段结束。
     *
     * @param question 用户问题
     * @return SSE 文本增量流
     */
    @Override
    public Flux<String> chat(String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }

        String trimmed = question.trim();
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("a2a-host")
                .sessionId("a2a-" + System.currentTimeMillis())
                .build();

        log.info("━━━━━━━ A2A Host 启动 ━━━━━━━ question={}", trimmed);

        return a2aHostAgent
                .streamEvents(new UserMessage(trimmed), ctx)
                .mapNotNull(this::mapEventToSseChunk)
                .doOnNext(chunk -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[SSE] chunk={}", abbreviate(chunk));
                    }
                })
                .doOnComplete(() -> log.info("━━━━━━━ A2A Host 完成 ━━━━━━━"))
                .doOnError(e -> log.error("A2A Host 异常", e))
                .onErrorResume(e -> Flux.just("错误: " + e.getMessage()));
    }

    /**
     * 将 AgentEvent 映射为 SSE 文本。
     * <p>
     * LLM 文本、A2A 工具开始、工具返回内容、工具结束状态都会推给前端。
     *
     * @param event AgentScope 流式事件
     * @return SSE 文本；无关事件返回 null（由 mapNotNull 过滤）
     */
    private String mapEventToSseChunk(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta) {
            String text = delta.getDelta();
            return (text == null || text.isEmpty()) ? null : text;
        }
        if (event instanceof ToolCallStartEvent toolCall) {
            String name = toolCall.getToolCallName();
            log.info("[A2A] 开始调用工具: {}", name);
            return "\n\n========== [A2A工具开始] " + name + " ==========\n";
        }
        if (event instanceof ToolResultTextDeltaEvent toolResult) {
            String text = toolResult.getDelta();
            return (text == null || text.isEmpty()) ? null : text;
        }
        if (event instanceof ToolResultEndEvent toolEnd) {
            String name = toolEnd.getToolCallName();
            Object state = toolEnd.getState();
            log.info("[A2A] 工具完成: {} state={}", name, state);
            return "\n========== [A2A工具结束] " + name
                    + " (" + state + ") ==========\n\n";
        }
        return null;
    }

    /**
     * 日志截断，避免超长 chunk 刷屏。
     *
     * @param text 原始文本
     * @return 截断后文本
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }
}
