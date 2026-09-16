package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态分层路由模型代理（DynamicRoutingChatModel）：
 * <p>
 * 实现 AgentScope {@link Model} 接口，作为 Host HarnessAgent 绑定的持久模型。
 * 解决多轮会话中「HarnessAgent 会话缓存」与「每轮根据问题复杂度动态路由 Fast/Strong 模型」的绑定冲突：
 * 每次调用 stream 时，动态从 Reactor Context 或会话映射中解析出当前轮次的真实模型执行流式生成。
 */
@Slf4j
@Component
public class DynamicRoutingChatModel implements Model {

    /** Reactor Context 中存放当前轮次路由模型的 Key */
    public static final String ROUTED_MODEL_KEY = "DYNAMIC_ROUTED_MODEL";

    /** Reactor Context 中存放当前会话 ID 的 Key */
    public static final String SESSION_ID_KEY = "DYNAMIC_ROUTED_SESSION_ID";

    private final OpenAIChatModel defaultModel;
    private final Map<String, Model> sessionActiveModel = new ConcurrentHashMap<>();

    public DynamicRoutingChatModel(OpenAIChatModel defaultModel) {
        this.defaultModel = defaultModel;
    }

    /**
     * 将当前轮次选出的模型绑定到指定 sessionId（作为双重保障）。
     *
     * @param sessionId 会话 ID
     * @param model     本次使用的模型
     */
    public void bindSessionModel(String sessionId, Model model) {
        if (sessionId != null && model != null) {
            sessionActiveModel.put(sessionId, model);
        }
    }

    /**
     * 解绑会话模型。
     *
     * @param sessionId 会话 ID
     */
    public void unbindSessionModel(String sessionId) {
        if (sessionId != null) {
            sessionActiveModel.remove(sessionId);
        }
    }

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.deferContextual(ctxView -> {
            Model model = resolveActiveModel(ctxView);
            return model.stream(messages, tools, options);
        });
    }

    /**
     * 解析当前激活的模型：Reactor Context 优先，其次会话映射，兜底默认模型。
     */
    private Model resolveActiveModel(ContextView ctx) {
        if (ctx.hasKey(ROUTED_MODEL_KEY)) {
            Object obj = ctx.get(ROUTED_MODEL_KEY);
            if (obj instanceof Model m) {
                return m;
            }
        }
        if (ctx.hasKey(SESSION_ID_KEY)) {
            String sid = ctx.get(SESSION_ID_KEY);
            Model m = sessionActiveModel.get(sid);
            if (m != null) {
                return m;
            }
        }
        return defaultModel;
    }

    @Override
    public String getModelName() {
        return defaultModel != null ? defaultModel.getModelName() : "dynamic-routed";
    }

    @Override
    public boolean supportsNativeStructuredOutput() {
        return defaultModel != null && defaultModel.supportsNativeStructuredOutput();
    }

    @Override
    public boolean supportsNativeStructuredOutputWithTools() {
        return defaultModel != null && defaultModel.supportsNativeStructuredOutputWithTools();
    }

    @Override
    public int getContextWindowSize() {
        return defaultModel != null ? defaultModel.getContextWindowSize() : 128000;
    }
}
