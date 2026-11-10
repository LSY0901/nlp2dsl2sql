package org.example.nlp2dsl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A2A Host SQL HITL 确认配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "a2a.host.confirm")
public class A2aHostConfirmProperties {

    /** 用户确认等待超时毫秒（默认 5 分钟，超时按拒绝处理） */
    private long timeoutMs = 300_000L;
}
