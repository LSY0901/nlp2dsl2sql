package org.example.nlp2dsl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A2A Host 可观测性 trace 配置（JSONL 落盘 + 内存记录器）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "a2a.host.trace")
public class A2aHostTraceProperties {

    /** 是否启用 trace 记录与 JSONL 导出 */
    private boolean enabled = true;

    /** JSONL 轨迹文件路径（append 追加） */
    private Path jsonlFile = Paths.get(".agentscope/traces/a2a-host.jsonl");

    /** 内存记录保留条数上限 */
    private int maxRecords = 200;

    /** 内存记录 TTL 毫秒（默认 1 小时） */
    private long ttlMs = 3_600_000L;
}
