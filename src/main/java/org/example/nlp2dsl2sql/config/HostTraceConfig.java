package org.example.nlp2dsl2sql.config;

import org.example.nlp2dsl2sql.a2a.trace.JsonlTraceMiddleware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * A2A Host trace 配置：按 {@link A2aHostTraceProperties#isEnabled()} 决定是否创建
 * {@link JsonlTraceMiddleware} 单例 Bean（append + flushEveryLine 落盘）。
 * <p>
 * 关闭时 Bean 不存在，工厂内通过 {@code ObjectProvider} 安全跳过挂载。
 */
@Configuration
@ConditionalOnProperty(
        prefix = "a2a.host.trace",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HostTraceConfig {

    /**
     * 全局 JSONL trace 中间件，挂到 Host / HITL Agent 上。
     *
     * @param properties trace 配置
     * @return JsonlTraceMiddleware 单例
     */
    @Bean(destroyMethod = "close")
    public JsonlTraceMiddleware a2aHostJsonlTraceMiddleware(
            A2aHostTraceProperties properties) throws IOException {
        return new JsonlTraceMiddleware(
                properties.getJsonlFile(), true, true);
    }
}
