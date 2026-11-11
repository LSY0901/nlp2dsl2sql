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
 * 会话流水写穿器：每轮对话结束时异步把问句、语义 DSL、截断回答结论与轮次类型落 PG，
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
     * @param archiveMapper    流水 Mapper
     * @param memoryProperties 记忆配置
     */
    public MemoryArchiveWriter(
            MemorySessionArchiveMapper archiveMapper,
            MemoryProperties memoryProperties) {
        this.archiveMapper = archiveMapper;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 异步写穿一轮完整对话流水（archive-enabled=false 时跳过）。
     *
     * @param sessionId         会话 ID
     * @param userId            归属用户 ID（已解析）
     * @param rawQuestion       用户原始问句
     * @param effectiveQuestion 改写后自包含问句
     * @param dsl               语义 DSL（非 SQL 轮次可为 null）
     * @param answer            截断后的自然语言回答结论（可为 null）
     * @param turnType          轮次类型（SQL / SOP / MIXED / CHITCHAT）
     */
    @Async("memoryArchiveExecutor")
    public void writeAsync(
            String sessionId, String userId,
            String rawQuestion, String effectiveQuestion,
            SemanticQueryDSL dsl, String answer, String turnType) {
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
            archive.setAnswer(answer);
            archive.setTurnType((turnType == null || turnType.isBlank()) ? "SQL" : turnType);
            archiveMapper.insert(archive);
            log.debug("[MemoryArchive] 流水落库 sessionId={}, turnType={}, question={}",
                    sessionId, archive.getTurnType(), rawQuestion);
        } catch (Exception e) {
            log.warn("[MemoryArchive] 流水写穿失败: sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
    }
}
