package org.example.nlp2dsl2sql.agent;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class Nlp2dslAgent {

    @Bean
    public HarnessAgent nlp2dslAgent(OpenAIChatModel model) {
        return HarnessAgent.builder()
                .name("NLP2DSL2SQL")
                .sysPrompt(
                        """
                                你是一个企业级自然语言转DSL再转SQL的数据查询Agent。
                                
                                你的职责：
                                1. 理解用户查询意图
                                2. 生成DSL查询计划
                                3. 调用数据库工具
                                4. 返回自然语言结果
                                """
                )
                // Spring自动注入
                .model(model)
                .workspace(
                        Paths.get(".agentscope/workspace")
                )
                .compaction(
                        CompactionConfig.builder()
                                //超过30轮压缩
                                .triggerMessages(30)
                                //保留最近10轮
                                .keepMessages(10)
                                .build()
                )
                .build();
    }

}
