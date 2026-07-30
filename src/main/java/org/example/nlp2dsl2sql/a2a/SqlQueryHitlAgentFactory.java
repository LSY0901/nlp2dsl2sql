package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.example.nlp2dsl2sql.agent.Nlp2dsl2sqlAgent;
import org.example.nlp2dsl2sql.tools.AgentToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 为 A2A Host 创建带 Permission HITL 的本地 SQL ReActAgent。
 * <p>
 * 每次 {@link #create()} 新建实例，避免多会话共用 Agent 状态。
 * 不修改对外 A2A 的 {@code sqlQueryReActAgent}。
 */
@Component
public class SqlQueryHitlAgentFactory {

    private static final String[] ALLOW_TOOLS = {
            "classify_intent",
            "retrieve_metadata",
            "generate_dsl",
            "validate_dsl",
            "enrich_dsl",
            "translate_sql",
            "review_sql"
    };

    private final OpenAIChatModel model;
    private final AgentToolRegistry toolRegistry;

    /**
     * @param model        LLM
     * @param toolRegistry 问数工具集
     */
    public SqlQueryHitlAgentFactory(
            OpenAIChatModel model,
            AgentToolRegistry toolRegistry) {
        this.model = model;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 创建 execute_sql 需人工确认的 SQL Agent。
     *
     * @return 新的 ReActAgent
     */
    public ReActAgent create() {
        PermissionContextState.Builder perm = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT);
        for (String name : ALLOW_TOOLS) {
            perm.addAllowRule(name, new PermissionRule(
                    name, null, PermissionBehavior.ALLOW, "hitl-policy"));
        }
        perm.addAskRule("execute_sql", new PermissionRule(
                "execute_sql", null, PermissionBehavior.ASK, "hitl-policy"));

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(toolRegistry);

        return ReActAgent.builder()
                .name("sql-query-hitl-agent")
                .sysPrompt(Nlp2dsl2sqlAgent.SUPERVISOR_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .permissionContext(perm.build())
                .maxIters(15)
                .build();
    }
}
