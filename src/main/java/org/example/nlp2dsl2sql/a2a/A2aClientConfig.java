package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;
import org.example.nlp2dsl2sql.config.A2aClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 装配两个远端 A2aAgent（SQL 回环 + SOP）。
 */
@Configuration
public class A2aClientConfig {

    /**
     * 本机 SQL A2A Agent 客户端。
     *
     * @param props 连接配置
     * @return A2aAgent
     */
    @Bean(name = "sqlQueryA2aAgent")
    public A2aAgent sqlQueryA2aAgent(A2aClientProperties props) {
        A2aClientProperties.AgentEndpoint ep = props.getSqlAgent();
        return A2aAgent.builder()
                .name("sql-query-agent")
                .agentCardResolver(buildCardResolver(ep))
                .build();
    }

    /**
     * 远端 SOP A2A Agent 客户端。
     *
     * @param props 连接配置
     * @return A2aAgent
     */
    @Bean(name = "sopDocA2aAgent")
    public A2aAgent sopDocA2aAgent(A2aClientProperties props) {
        A2aClientProperties.AgentEndpoint ep = props.getSopAgent();
        return A2aAgent.builder()
                .name("sop-doc-agent")
                .agentCardResolver(buildCardResolver(ep))
                .build();
    }

    /**
     * 按 endpoint 配置构建 WellKnown AgentCard 解析器。
     *
     * @param ep 单 Agent 连接信息
     * @return AgentCard 解析器
     */
    private WellKnownAgentCardResolver buildCardResolver(
            A2aClientProperties.AgentEndpoint ep) {
        return WellKnownAgentCardResolver.builder()
                .baseUrl(ep.getBaseUrl())
                .relativeCardPath(ep.getAgentCardPath())
                .authHeaders(Map.of())
                .build();
    }
}
