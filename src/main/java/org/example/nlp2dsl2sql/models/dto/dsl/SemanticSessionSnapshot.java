package org.example.nlp2dsl2sql.models.dto.dsl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 单轮问数语义快照（用于会话级多轮槽位继承与追问消歧）。
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

    /** 意图类型 */
    private String intent;

    /** 语义 DSL */
    private SemanticQueryDSL dsl;

    /** 富化后 DSL */
    private EnrichedQueryDSL enrichedDsl;

    /** 生成的 SQL */
    private String sql;

    /** 查询结果集 */
    private List<Map<String, Object>> queryResult;

    /** 生成时间戳 */
    private Instant timestamp;
}
