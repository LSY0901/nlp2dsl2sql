package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.example.nlp2dsl2sql.a2a.trace.JsonlTraceMiddleware;
import org.example.nlp2dsl2sql.service.memory.impl.SpringRedisAgentStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A2A Host Agent 工厂：按路由选出的模型与用户长期记忆提示词每次请求构建无状态 {@link ReActAgent}。
 * <p>
 * 彻底移除本地 Workspace（{@code .agentscope/workspace-a2a-host}）与进程内会话缓存，
 * 短期多轮消息状态统一交由 {@link SpringRedisAgentStateStore} 按 {@code RuntimeContext(userId, sessionId)} 托管。
 */
@Component
public class A2aHostAgentFactory {

    /** Agent 名，同时作为 Redis 状态键命名空间 */
    public static final String AGENT_NAME = "a2aHostAgent";

    private final A2aRemoteAgentTools tools;
    private final DynamicRoutingChatModel dynamicModel;
    private final SpringRedisAgentStateStore stateStore;
    private final ObjectProvider<JsonlTraceMiddleware> traceMiddleware;

    /**
     * 兼容 3 参构造（仅供单测便捷构造）。
     */
    public A2aHostAgentFactory(
            A2aRemoteAgentTools tools,
            DynamicRoutingChatModel dynamicModel,
            ObjectProvider<JsonlTraceMiddleware> traceMiddleware) {
        this(tools, dynamicModel, null, traceMiddleware);
    }

    /**
     * @param tools           A2A 远程 Agent 工具
     * @param dynamicModel    动态分层路由模型代理
     * @param stateStore      Redis 会话状态存储
     * @param traceMiddleware JSONL trace 中间件（trace 关闭时不存在）
     */
    @Autowired
    public A2aHostAgentFactory(
            A2aRemoteAgentTools tools,
            DynamicRoutingChatModel dynamicModel,
            SpringRedisAgentStateStore stateStore,
            ObjectProvider<JsonlTraceMiddleware> traceMiddleware) {
        this.tools = tools;
        this.dynamicModel = dynamicModel;
        this.stateStore = stateStore;
        this.traceMiddleware = traceMiddleware;
    }

    /**
     * 创建基于动态路由模型与默认系统提示词的 Host Agent。
     *
     * @return 名为 a2aHostAgent 的 ReActAgent
     */
    public ReActAgent create() {
        return create(dynamicModel, A2aHostPrompt.SYSTEM);
    }

    /**
     * 创建指定模型与默认系统提示词的 Host Agent。
     *
     * @param model 目标模型
     * @return 名为 a2aHostAgent 的 ReActAgent
     */
    public ReActAgent create(Model model) {
        return create(model, A2aHostPrompt.SYSTEM);
    }

    /**
     * 创建指定模型与自定义系统提示词（已拼入 PG 用户长期记忆）的 Host Agent。
     *
     * @param model           目标模型
     * @param customSysPrompt 系统提示词（为空时回退 {@link A2aHostPrompt#SYSTEM}）
     * @return 名为 a2aHostAgent 的 ReActAgent
     */
    public ReActAgent create(Model model, String customSysPrompt) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);

        String effectivePrompt = (customSysPrompt == null || customSysPrompt.isBlank())
                ? A2aHostPrompt.SYSTEM
                : customSysPrompt;

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(effectivePrompt)
                .model(model)
                .toolkit(toolkit)
                .maxIters(10)
                // SSE 中断时会话可能残留未完成的 tool_call；开启后自动补错误结果继续对话。
                .enablePendingToolRecovery(true);

        if (stateStore != null) {
            // 按 Agent 名隔离 Redis 状态键，避免与同 sessionId 的 ReAct/Skill 模式互相恢复消息
            builder.stateStore(stateStore.forAgent(AGENT_NAME));
        }

        JsonlTraceMiddleware middleware = traceMiddleware != null
                ? traceMiddleware.getIfAvailable()
                : null;
        if (middleware != null) {
            builder.middleware(middleware);
        }
        return builder.build();
    }
}
