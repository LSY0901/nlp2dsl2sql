package org.example.nlp2dsl2sql.config;

import io.agentscope.core.hook.recorder.JsonlTraceExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A2A Host trace 配置：按 {@link A2aHostTraceProperties#isEnabled()} 决定是否创建
 * {@link JsonlTraceExporter} 单例 Bean（append + flushEveryLine 落盘）。
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
     * 全局 JSONL trace 导出器，挂到 Host / HITL Agent 上。
     *
     * @param properties trace 配置
     * @return JsonlTraceExporter 单例
     */
    @Bean(destroyMethod = "close")
    public JsonlTraceExporter a2aHostJsonlTraceExporter(
            A2aHostTraceProperties properties) {
        return JsonlTraceExporter.builder(properties.getJsonlFile())
                .append(true)
                .flushEveryLine(true)
                .build();
    }
}
