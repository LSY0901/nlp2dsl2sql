package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.example.nlp2dsl2sql.agent.Nlp2dsl2sqlAgent;
import org.example.nlp2dsl2sql.tools.AgentToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 供 A2A Server 暴露的 SQL 查询 ReActAgent（复用问数 Toolkit）。
 * <p>
 * Bean 名称 {@code sqlQueryReActAgent}，与 {@code nlp2dsl2sqlAgentLatest} 隔离。
 */
@Configuration
public class SqlQueryA2aServerAgentConfig {

    /**
     * A2A 对外的 sql-query-agent。
     *
     * @param model        LLM
     * @param toolRegistry 现有问数工具
     * @return ReActAgent
     */
    @Bean(name = "sqlQueryReActAgent")
    public ReActAgent sqlQueryReActAgent(
            OpenAIChatModel model,
            AgentToolRegistry toolRegistry) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(toolRegistry);
        return ReActAgent.builder()
                .name("sql-query-agent")
                .sysPrompt(Nlp2dsl2sqlAgent.SUPERVISOR_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .maxIters(15)
                .build();
    }
}
