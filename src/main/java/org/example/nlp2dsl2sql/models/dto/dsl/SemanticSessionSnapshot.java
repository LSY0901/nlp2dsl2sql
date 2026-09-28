package org.example.nlp2dsl2sql.models.dto.dsl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 单轮问数语义快照（用于会话级多轮追问消歧）。
 * <p>
 * 只保留改写下一轮问句真正需要的信息：上一轮问句 + 语义 DSL。
 * 查询结果、SQL、富化 DSL 一律不入快照——复用历史行集回答等于返回过期数据。
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

    /** 语义 DSL */
    private SemanticQueryDSL dsl;

    /** 生成时间戳 */
    private Instant timestamp;
}
