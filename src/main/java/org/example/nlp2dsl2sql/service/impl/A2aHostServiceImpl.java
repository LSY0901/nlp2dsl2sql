package org.example.nlp2dsl2sql.service.impl;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.a2a.A2aHostSessionManager;
import org.example.nlp2dsl2sql.a2a.A2aHostChatContext;
import org.example.nlp2dsl2sql.a2a.A2aHostModelRouter;
import org.example.nlp2dsl2sql.a2a.DynamicRoutingChatModel;
import org.example.nlp2dsl2sql.a2a.A2aSqlConfirmRegistry;
import org.example.nlp2dsl2sql.a2a.A2aSqlConfirmTexts;
import org.example.nlp2dsl2sql.a2a.trace.AgentEventToolTracer;
import org.example.nlp2dsl2sql.a2a.trace.HostTraceRecord;
import org.example.nlp2dsl2sql.a2a.trace.HostTraceRecorder;
import org.example.nlp2dsl2sql.models.vo.A2aHostConfirmRequest;
import org.example.nlp2dsl2sql.models.vo.A2aHostConfirmResponse;
import org.example.nlp2dsl2sql.service.IA2aHostService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.List;
import java.util.UUID;

/**
 * A2A Host SSE 服务（含 SQL HITL 确认桥接）。
 * <p>
 * Host Agent 每次请求按问题复杂度路由模型并新建，避免单例绑定单一模型。
 */
@Slf4j
@Service
public class A2aHostServiceImpl implements IA2aHostService {

    private final A2aHostSessionManager sessionManager;
    private final A2aHostModelRouter modelRouter;
    private final A2aSqlConfirmRegistry confirmRegistry;
    private final HostTraceRecorder traceRecorder;
    private final DynamicRoutingChatModel dynamicModel;

    /**
     * 构造 A2A Host 服务。
     *
     * @param sessionManager   Host Agent 会话缓存管理器
     * @param modelRouter      模型路由器
     * @param confirmRegistry  SQL 确认挂起表
     * @param traceRecorder    trace 内存记录器
     * @param dynamicModel     动态路由模型代理
     */
    public A2aHostServiceImpl(
            A2aHostSessionManager sessionManager,
            A2aHostModelRouter modelRouter,
            A2aSqlConfirmRegistry confirmRegistry,
            HostTraceRecorder traceRecorder,
            DynamicRoutingChatModel dynamicModel) {
        this.sessionManager = sessionManager;
        this.modelRouter = modelRouter;
        this.confirmRegistry = confirmRegistry;
        this.traceRecorder = traceRecorder;
        this.dynamicModel = dynamicModel;
    }

    /**
     * 启动 Host Agent，合并 Agent 事件流与 HITL SSE 桥。
     *
     * @param sessionId 会话 ID
     * @param question  用户问题
     * @return SSE 文本增量流
     */
    @Override
    public Flux<String> chat(String sessionId, String question) {
        return chat(sessionId, null, question);
    }

    /**
     * 启动 Host Agent，合并 Agent 事件流与 HITL SSE 桥（带租户/用户身份）。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID（可选）
     * @param question  用户问题
     * @return SSE 文本增量流
     */
    @Override
    public Flux<String> chat(String sessionId, String userId, String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }

        String trimmed = question.trim();
        String sid = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId.trim();
        String uid = (userId != null && !userId.isBlank())
                ? userId.trim()
                : "user_" + (sid.length() > 8 ? sid.substring(0, 8) : sid);

        A2aHostChatContext hostCtx = new A2aHostChatContext(sid, uid);
        traceRecorder.start(sid, trimmed);

        A2aHostModelRouter.ModelRoute route = modelRouter.route(trimmed);
        OpenAIChatModel model = route.model();
        traceRecorder.recordModel(sid, route.tier(), model.getModelName());
        dynamicModel.bindSessionModel(sid, model);
        HarnessAgent hostAgent = sessionManager.getOrCreateAgent(sid, model);

        RuntimeContext ctx = RuntimeContext.builder()
                .userId(uid)
                .sessionId(sid)
                .put(A2aHostChatContext.class, hostCtx)
                .build();

        AgentEventToolTracer hostTracer =
                new AgentEventToolTracer(traceRecorder, sid, "host");

        log.info("━━━━━━━ A2A Host 启动 ━━━━━━━ sessionId={}, userId={}, tier={}, model={}, question={}",
                sid, uid, route.tier(), model.getModelName(), trimmed);

        HostTraceRecorder.TimedStep loopTimer =
                traceRecorder.timedStep(sid, "host-loop", null);

        Flux<String> agentFlux = hostAgent
                .streamEvents(new UserMessage(trimmed), ctx)
                .mapNotNull(event -> {
                    hostTracer.onEvent(event);
                    return mapEventToSseChunk(event);
                })
                .doOnNext(chunk -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[SSE] chunk={}", abbreviate(chunk));
                    }
                })
                .doFinally(signal -> {
                    hostTracer.endAll();
                    hostCtx.complete();
                    loopTimer.detail("signal=" + signal);
                    loopTimer.close();
                    log.info("━━━━━━━ A2A Host 完成 signal={} ━━━━━━━",
                            signal);
                });

        Flux<String> bridgeFlux = hostCtx.getSseSink().asFlux();
        Flux<String> head = Flux.just(
                "sessionId: " + sid + "\n",
                "tier: " + route.tier() + "\n",
                "model: " + model.getModelName() + "\n");

        return Flux.merge(head, agentFlux, bridgeFlux)
                .doOnError(e -> {
                    log.error("A2A Host 异常", e);
                    traceRecorder.fail(sid,
                            HostTraceRecord.STATUS_FAILED, e.getMessage());
                })
                .doFinally(signal -> {
                    dynamicModel.unbindSessionModel(sid);
                    if (signal == SignalType.ON_COMPLETE) {
                        traceRecorder.finish(sid,
                                HostTraceRecord.STATUS_COMPLETED);
                    } else if (signal != SignalType.ON_ERROR) {
                        traceRecorder.fail(sid,
                                HostTraceRecord.STATUS_CANCELLED,
                                "signal=" + signal);
                    }
                })
                .onErrorResume(e -> Flux.just("错误: " + e.getMessage()))
                .contextWrite(reactor.util.context.Context.of(
                        DynamicRoutingChatModel.ROUTED_MODEL_KEY, model,
                        DynamicRoutingChatModel.SESSION_ID_KEY, sid));
    }

    /**
     * 按 rawInput 判定并完成挂起决策。
     *
     * @param request 确认请求
     * @return 响应
     */
    @Override
    public A2aHostConfirmResponse confirm(A2aHostConfirmRequest request) {
        if (request == null
                || request.getSessionId() == null
                || request.getSessionId().isBlank()) {
            return A2aHostConfirmResponse.fail("sessionId 不能为空");
        }
        String sid = request.getSessionId().trim();
        boolean approved = A2aSqlConfirmTexts.isApproved(request.getRawInput());
        log.info("[HITL] confirm sessionId={}, rawInput={}, approved={}",
                sid, request.getRawInput(), approved);
        boolean ok = confirmRegistry.complete(sid, approved);
        if (!ok) {
            return A2aHostConfirmResponse.fail("无待确认 SQL 或已过期");
        }
        return A2aHostConfirmResponse.ok(
                approved ? "已批准执行" : "已取消执行");
    }

    /**
     * 最近 trace 列表（按开始时间倒序）。
     *
     * @return trace 列表
     */
    @Override
    public List<HostTraceRecord> listTraces() {
        return traceRecorder.listRecent();
    }

    /**
     * 单条 trace 详情。
     *
     * @param sessionId 会话 ID
     * @return trace；不存在返回 null
     */
    @Override
    public HostTraceRecord getTrace(String sessionId) {
        return traceRecorder.get(sessionId);
    }

    /**
     * 将 AgentEvent 映射为 SSE 文本。
     *
     * @param event AgentScope 流式事件
     * @return SSE 文本；无关事件返回 null
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
     * 日志截断。
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
