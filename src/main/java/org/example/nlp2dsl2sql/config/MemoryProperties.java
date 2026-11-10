package org.example.nlp2dsl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 会话语义记忆配置（短期 Redis + 长期 PG）。
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

    /** 长期记忆配置 */
    private Longterm longterm = new Longterm();

    /**
     * 长期记忆（PG 流水/摘要/画像）配置。
     */
    @Data
    public static class Longterm {

        /** 是否流水写穿 PG（false 时只进 Redis） */
        private boolean archiveEnabled = true;

        /** 定时压缩任务总开关 */
        private boolean jobsEnabled = true;

        /** Redis 轮次流水保留条数 */
        private int turnsKeep = 50;

        /** 摘要任务配置 */
        private Summary summary = new Summary();

        /** 画像任务配置 */
        private Profile profile = new Profile();
    }

    /**
     * 摘要任务配置。
     */
    @Data
    public static class Summary {

        /** 会话结束判定：末轮距今超过该分钟数 */
        private int idleMinutes = 30;

        /** 扫描周期毫秒 */
        private long scanFixedDelayMs = 600_000L;
    }

    /**
     * 画像任务配置。
     */
    @Data
    public static class Profile {

        /** 画像提炼周期毫秒 */
        private long refreshFixedDelayMs = 3_600_000L;

        /** 改写 prompt 注入画像时截断字数 */
        private int maxChars = 300;
    }
}
