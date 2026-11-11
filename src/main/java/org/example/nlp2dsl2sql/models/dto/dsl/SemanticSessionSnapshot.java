package org.example.nlp2dsl2sql.models.dto.dsl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 单轮问数语义快照与轮次流水记录（用于会话级多轮追问消歧与最近轮次查看）。
 * <p>
 * 快照槽位（snapshot）用于改写下一轮问句（上一轮问句 + 语义 DSL）；
 * 轮次流水（turns）额外保留截断后的自然语言回答结论与轮次类型（SQL / SOP / MIXED / CHITCHAT）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSessionSnapshot {

    /** 原始子问题 */
    private String question;

    /** 上下文补全/改写后的自包含问题 */
    private String effectiveQuestion;

    /** 语义 DSL（仅 SQL 查询成功时非空） */
    private SemanticQueryDSL dsl;

    /** 截断后的回答结论（最多 memory.answer-max-chars 字） */
    private String answer;

    /** 轮次类型：SQL / SOP / MIXED / CHITCHAT */
    private String turnType;

    /** 生成时间戳 */
    private Instant timestamp;
}
