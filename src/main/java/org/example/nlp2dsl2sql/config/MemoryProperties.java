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
     * 会话短期记忆（AgentState + 语义快照）闲置过期时间。
     * <p>
     * 取值 24h 而非分钟级：前端 sessionId 落 localStorage 后会长期复用同一 key，
     * 快照本身只有几 KB 文本，分钟级 TTL 会让"跨天回来追问"直接失忆。
     */
    private Duration sessionTtl = Duration.ofHours(24);

    /**
     * 短期 AgentState 消息滑动窗口上限（超限时按 USER 消息边界截断并异步触发 PG 摘要压缩）。
     */
    private int maxStateMessages = 30;

    /**
     * 单轮回答落库/落 Redis 时的最大截断字符数。
     */
    private int answerMaxChars = 500;

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

        /** 窗口截断触发摘要的节流阈值：自上次摘要以来新增轮次不少于该值才调用 LLM */
        private int evictMinNewTurns = 5;

        /** A2A Host 启动时注入最近多少条历史会话摘要 */
        private int injectRecentCount = 3;

        /** A2A Host 启动时注入历史会话摘要的总字符上限 */
        private int injectMaxChars = 400;
    }

    /**
     * 画像任务配置。
     */
    @Data
    public static class Profile {

        /** 画像提炼周期毫秒 */
        private long refreshFixedDelayMs = 3_600_000L;

        /** 改写/Host prompt 注入画像时截断字数 */
        private int maxChars = 300;
    }
}
