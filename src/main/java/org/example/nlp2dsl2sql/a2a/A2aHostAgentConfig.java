package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * A2A Host HarnessAgent 配置。
 */
@Configuration
public class A2aHostAgentConfig {

    /**
     * Host Agent：只注册 A2A 远程工具。
     *
     * @param model LLM 模型
     * @param tools A2A 远程 Agent 工具
     * @return 名为 a2aHostAgent 的 HarnessAgent
     */
    @Bean(name = "a2aHostAgent")
    public HarnessAgent a2aHostAgent(
            OpenAIChatModel model,
            A2aRemoteAgentTools tools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);

        return HarnessAgent.builder()
                .name("a2aHostAgent")
                .sysPrompt(A2aHostPrompt.SYSTEM)
                .model(model)
                .toolkit(toolkit)
                .maxIters(10)
                .workspace(Paths.get(".agentscope/workspace-a2a-host"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
