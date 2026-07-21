package org.example.nlp2dsl2sql.semanticdsl.agent;

import org.example.nlp2dsl2sql.semanticdsl.tools.MultiAgentSessionContext;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 多 Agent 协作服务实现 — HarnessAgent 内置 ReAct + {@code @Tool} Toolkit。
 * <p>
 * 通过 {@link HarnessAgent#streamEvents} 真流式输出到前端：
 * <ul>
 *   <li>{@link TextBlockDeltaEvent} — LLM token 增量</li>
 *   <li>{@link ToolCallStartEvent} — 工具开始调用</li>
 *   <li>{@link ToolResultTextDeltaEvent} — 工具返回内容</li>
 *   <li>{@link ToolResultEndEvent} — 工具执行结束状态</li>
 * </ul>
 */
@Slf4j
@Service
public class MultiAgentServiceImpl implements IMultiAgentService {

    private final HarnessAgent nlp2dsl2sqlAgentLatest;

    /**
     * 构造多 Agent 服务。
     *
     * @param nlp2dsl2sqlAgentLatest
     */
    public MultiAgentServiceImpl(
            @Qualifier("nlp2dsl2sqlAgentLatest") HarnessAgent nlp2dsl2sqlAgentLatest) {
        this.nlp2dsl2sqlAgentLatest = nlp2dsl2sqlAgentLatest;
    }

    /**
     * 多 Agent 协作查询（SSE 真流式）。
     * <p>
     * 订阅后立即启动 HarnessAgent ReAct；LLM token、工具调用与工具返回
     * 均推送到前端展示，全程不阻塞等待整段结束。
     *
     * @param question 用户自然语言问题
     * @return SSE 文本增量流
     */
    @Override
    public Flux<String> multiAgentQuery(String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }

        String trimmed = question.trim();
        // 每次查询使用独立 session，避免复用历史对话直接“背答案”跳过工具链。
        // 若需要多轮对话，应由前端传入稳定的 sessionId。
        MultiAgentSessionContext session = new MultiAgentSessionContext();
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("lsy")
                .sessionId("0901")
                .put(MultiAgentSessionContext.class, session)
                .build();

        log.info("━━━━━━━ HarnessAgent 流式 ReAct 启动 ━━━━━━━");

        return nlp2dsl2sqlAgentLatest
                .streamEvents(new UserMessage(trimmed), ctx)
                .mapNotNull(this::mapEventToSseChunk)
                .doOnNext(chunk -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[SSE] chunk={}", abbreviate(chunk));
                    }
                })
                .doOnComplete(() ->
                        log.info("━━━━━━━ HarnessAgent 流式 ReAct 完成 ━━━━━━━"))
                .doOnError(e -> log.error("HarnessAgent 流式 ReAct 异常", e))
                .onErrorResume(e -> Flux.just("错误: " + e.getMessage()));
    }

    /**
     * 将 AgentEvent 映射为前端可读的 SSE 文本块。
     * <p>
     * LLM 文本、工具开始、工具返回内容、工具结束状态都会推给前端。
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
            log.info("[ReAct] 开始调用工具: {}", name);
            return "\n\n========== [工具开始] " + name + " ==========\n";
        }
        if (event instanceof ToolResultTextDeltaEvent toolResult) {
            String text = toolResult.getDelta();
            return (text == null || text.isEmpty()) ? null : text;
        }
        if (event instanceof ToolResultEndEvent toolEnd) {
            String name = toolEnd.getToolCallName();
            Object state = toolEnd.getState();
            log.info("[ReAct] 工具完成: {} state={}", name, state);
            return "\n========== [工具结束] " + name + " (" + state + ") ==========\n\n";
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
