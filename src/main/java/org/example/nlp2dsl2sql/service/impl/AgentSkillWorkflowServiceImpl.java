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
import org.example.nlp2dsl2sql.service.memory.QueryPreprocessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * Agent-Skill-Workflow 服务（HarnessAgent + SkillToolGroup）。
 * <p>
 * userId / sessionId 写死，启用 Harness 会话记忆。
 */
@Slf4j
@Service
public class AgentSkillWorkflowServiceImpl implements IAgentSkillWorkflowService {

    private final HarnessAgent skillHarnessAgent;
    private final QueryPreprocessor queryPreprocessor;

    /**
     * 构造服务。
     *
     * @param skillHarnessAgent Skill Harness Agent
     * @param queryPreprocessor  多轮记忆入口
     */
    public AgentSkillWorkflowServiceImpl(
            @Qualifier("nlp2dsl2sqlSkillAgent")
            HarnessAgent skillHarnessAgent,
            QueryPreprocessor queryPreprocessor) {
        this.skillHarnessAgent = skillHarnessAgent;
        this.queryPreprocessor = queryPreprocessor;
    }

    /**
     * 执行 Skill Harness 查询。
     *
     * @param question 用户问题
     * @return SSE 流
     */
    @Override
    public Flux<String> run(String question) {
        return run(null, null, question);
    }

    /**
     * 执行 Skill Harness 查询（带 sessionId 与 userId）。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param question  用户问题
     * @return SSE 流
     */
    @Override
    public Flux<String> run(String sessionId, String userId, String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }
        String rawQuestion = question.trim();
        String sid = (sessionId != null && !sessionId.isBlank())
                ? sessionId.trim()
                : java.util.UUID.randomUUID().toString();
        String uid = (userId != null && !userId.isBlank())
                ? userId.trim()
                : "user_" + (sid.length() > 8 ? sid.substring(0, 8) : sid);
        String trimmed = queryPreprocessor.preprocess(sid, uid, rawQuestion);

        AgentSessionContext session = new AgentSessionContext();
        StringBuilder answerBuf = new StringBuilder();
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(uid)
                .sessionId(sid)
                .put(AgentSessionContext.class, session)
                .build();

        log.info("━━━━━━━ Skill-Harness 启动 userId={}, sessionId={} ━━━━━━━",
                uid, sid);

        return skillHarnessAgent
                .streamEvents(new UserMessage(trimmed), ctx)
                .mapNotNull(event -> {
                    if (event instanceof TextBlockDeltaEvent delta
                            && delta.getDelta() != null) {
                        answerBuf.append(delta.getDelta());
                    }
                    return mapEventToSseChunk(event);
                })
                // 正常完成与客户端断开都归档：断开时 Redis AgentState 已回写本轮消息，
                // 只在完成时归档会造成短期有、长期无的不一致
                .doFinally(signal -> {
                    if (signal == SignalType.ON_ERROR) {
                        return;
                    }
                    queryPreprocessor.rememberTurn(
                            sid, uid, rawQuestion, trimmed,
                            session.getDsl(), answerBuf.toString(),
                            session.getDsl() != null ? "SQL" : "CHITCHAT");
                    log.info("━━━━━━━ Skill-Harness 结束 signal={} ━━━━━━━", signal);
                })
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
