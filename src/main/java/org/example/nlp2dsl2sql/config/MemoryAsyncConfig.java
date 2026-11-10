package org.example.nlp2dsl2sql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 记忆归档异步配置：流水写穿专用单线程池，隔离查询主流程。
 */
@Configuration
@EnableAsync
public class MemoryAsyncConfig {

    /**
     * 流水写穿线程池：核心 1 / 上限 2 / 队列 200，写失败仅告警不影响查询。
     */
    @Bean("memoryArchiveExecutor")
    public ThreadPoolTaskExecutor memoryArchiveExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("memory-archive-");
        executor.initialize();
        return executor;
    }
}
