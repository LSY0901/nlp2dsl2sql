package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.example.nlp2dsl2sql.a2a.trace.JsonlTraceMiddleware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

/**
 * A2A Host Agent 工厂：按路由选出的模型每次请求新建 Host Agent。
 * <p>
 * 与 {@link SqlQueryHitlAgentFactory} 每查询新建模式一致，避免单例绑定单一模型。
 */
@Component
public class A2aHostAgentFactory {

    private final A2aRemoteAgentTools tools;
    private final ObjectProvider<JsonlTraceMiddleware> traceMiddleware;

    /**
     * @param tools           A2A 远程 Agent 工具
     * @param traceMiddleware JSONL trace 中间件（trace 关闭时不存在）
     */
    public A2aHostAgentFactory(
            A2aRemoteAgentTools tools,
            ObjectProvider<JsonlTraceMiddleware> traceMiddleware) {
        this.tools = tools;
        this.traceMiddleware = traceMiddleware;
    }

    /**
     * 创建 Host Agent。
     *
     * @param model 路由选出的模型
     * @return 名为 a2aHostAgent 的 HarnessAgent
     */
    public HarnessAgent create(OpenAIChatModel model) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("a2aHostAgent")
                .sysPrompt(A2aHostPrompt.SYSTEM)
                .model(model)
                .toolkit(toolkit)
                .maxIters(10)
                .workspace(Paths.get(".agentscope/workspace-a2a-host"))
                // SSE 中断时会话可能残留未完成的 tool_call；开启后自动补错误结果继续对话。
                .enablePendingToolRecovery(true)
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDefaultWorkspaceSkills()
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build());

        JsonlTraceMiddleware middleware = traceMiddleware.getIfAvailable();
        if (middleware != null) {
            builder.middleware(middleware);
        }
        return builder.build();
    }
}
