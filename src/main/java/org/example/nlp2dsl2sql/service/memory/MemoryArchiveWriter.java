package org.example.nlp2dsl2sql.service.memory;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionArchiveMapper;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionArchive;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 会话流水写穿器：每轮 remember 时异步把问句与 DSL 原样落 PG，
 * 作为长期记忆（摘要/画像）的压缩原料。
 * <p>
 * 走 {@code memoryArchiveExecutor} 线程池，失败仅 log.warn，绝不阻塞查询主流程。
 */
@Slf4j
@Component
public class MemoryArchiveWriter {

    private final MemorySessionArchiveMapper archiveMapper;
    private final MemoryProperties memoryProperties;

    /**
     * @param archiveMapper   流水 Mapper
     * @param memoryProperties 记忆配置
     */
    public MemoryArchiveWriter(
            MemorySessionArchiveMapper archiveMapper,
            MemoryProperties memoryProperties) {
        this.archiveMapper = archiveMapper;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 异步写穿一轮流水（archive-enabled=false 时跳过）。
     *
     * @param sessionId        会话 ID
     * @param userId           归属用户 ID（已解析）
     * @param rawQuestion      用户原始问句
     * @param effectiveQuestion 改写后自包含问句
     * @param dsl              语义 DSL（remember 链路保证非空）
     */
    @Async("memoryArchiveExecutor")
    public void writeAsync(
            String sessionId, String userId,
            String rawQuestion, String effectiveQuestion,
            SemanticQueryDSL dsl) {
        if (!memoryProperties.getLongterm().isArchiveEnabled()) {
            return;
        }
        try {
            MemorySessionArchive archive = new MemorySessionArchive();
            archive.setSessionId(sessionId);
            archive.setUserId(userId);
            archive.setQuestion(rawQuestion);
            archive.setEffectiveQuestion(effectiveQuestion);
            archive.setDslJson(dsl == null ? null : JSON.toJSONString(dsl));
            archiveMapper.insert(archive);
            log.debug("[MemoryArchive] 流水落库 sessionId={}, question={}",
                    sessionId, rawQuestion);
        } catch (Exception e) {
            log.warn("[MemoryArchive] 流水写穿失败: sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
    }
}
