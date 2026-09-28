package org.example.nlp2dsl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 会话语义记忆配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /**
     * 会话语义快照闲置过期时间。
     * <p>
     * 取值 24h 而非分钟级：前端 sessionId 落 localStorage 后会长期复用同一 key，
     * 快照本身只有几 KB 文本，分钟级 TTL 会让"跨天回来追问"直接失忆。
     */
    private Duration sessionTtl = Duration.ofHours(24);
}
