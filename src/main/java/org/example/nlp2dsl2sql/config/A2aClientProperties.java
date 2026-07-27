package org.example.nlp2dsl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A2A 客户端连接配置（SQL / SOP 远端 Agent）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "a2a")
public class A2aClientProperties {

    /** 本机 SQL A2A Server（回环调用） */
    private AgentEndpoint sqlAgent = new AgentEndpoint();

    /** 远端 SOP A2A Server */
    private AgentEndpoint sopAgent = new AgentEndpoint();

    /**
     * 单个远端 Agent 连接信息。
     */
    @Data
    public static class AgentEndpoint {
        /** 服务根地址，如 http://127.0.0.1:9002 */
        private String baseUrl = "http://127.0.0.1:9002";
        /** AgentCard 路径 */
        private String agentCardPath = "/.well-known/agent-card.json";
        /** 调用超时毫秒 */
        private long timeoutMs = 60000L;
    }
}
