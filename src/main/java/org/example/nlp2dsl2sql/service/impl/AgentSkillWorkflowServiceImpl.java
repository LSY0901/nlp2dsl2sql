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
import org.example.nlp2dsl2sql.models.dto.dsl.AgentSessionContext;
import org.example.nlp2dsl2sql.service.IAgentSkillWorkflowService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agent-Skill-Workflow 服务（HarnessAgent + SkillToolGroup）。
 * <p>
 * userId / sessionId 写死，启用 Harness 会话记忆。
 */
@Slf4j
@Service
public class AgentSkillWorkflowServiceImpl implements IAgentSkillWorkflowService {

    private static final String USER_ID = "lsy";
    private static final String SESSION_ID = "0901";

    private final HarnessAgent skillHarnessAgent;

    /**
     * 构造服务。
     *
     * @param skillHarnessAgent Skill Harness Agent
     */
    public AgentSkillWorkflowServiceImpl(
            @Qualifier("nlp2dsl2sqlSkillAgent")
            HarnessAgent skillHarnessAgent) {
        this.skillHarnessAgent = skillHarnessAgent;
    }

    /**
     * 执行 Skill Harness 查询。
     *
     * @param question 用户问题
     * @return SSE 流
     */
    @Override
    public Flux<String> run(String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }
        String trimmed = question.trim();
        AgentSessionContext session = new AgentSessionContext();
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(USER_ID)
                .sessionId(SESSION_ID)
                .put(AgentSessionContext.class, session)
                .build();

        log.info("━━━━━━━ Skill-Harness 启动 userId={}, sessionId={} ━━━━━━━",
                USER_ID, SESSION_ID);

        return skillHarnessAgent
                .streamEvents(new UserMessage(trimmed), ctx)
                .mapNotNull(this::mapEventToSseChunk)
                .doOnComplete(() ->
                        log.info("━━━━━━━ Skill-Harness 完成 ━━━━━━━"))
                .doOnError(e -> log.error("Skill-Harness 异常", e))
                .onErrorResume(e -> Flux.just("错误: " + e.getMessage()));
    }

    /**
     * 将 AgentEvent 映射为 SSE 文本。
     *
     * @param event 框架事件
     * @return 文本块；无关事件返回 null
     */
    private String mapEventToSseChunk(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta) {
            String text = delta.getDelta();
            return (text == null || text.isEmpty()) ? null : text;
        }
        if (event instanceof ToolCallStartEvent toolCall) {
            String name = toolCall.getToolCallName();
            log.info("[Skill-Harness] 开始调用工具: {}", name);
            return "\n\n========== [工具开始] " + name + " ==========\n";
        }
        if (event instanceof ToolResultTextDeltaEvent toolResult) {
            String text = toolResult.getDelta();
            return (text == null || text.isEmpty()) ? null : text;
        }
        if (event instanceof ToolResultEndEvent toolEnd) {
            String name = toolEnd.getToolCallName();
            Object state = toolEnd.getState();
            log.info("[Skill-Harness] 工具完成: {} state={}", name, state);
            return "\n========== [工具结束] " + name
                    + " (" + state + ") ==========\n\n";
        }
        return null;
    }
}
