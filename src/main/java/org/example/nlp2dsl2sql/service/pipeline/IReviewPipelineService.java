package org.example.nlp2dsl2sql.service.pipeline;

import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;

/**
 * SQL 审查 Pipeline Service。
 */
public interface IReviewPipelineService {

    /**
     * 审查 SQL 正确性。
     *
     * @param sql         待审查 SQL
     * @param enrichedDSL 富化 DSL（构建 schema）
     * @param question    用户问题
     * @return 审查结果
     */
    ReviewResult review(String sql, EnrichedQueryDSL enrichedDSL, String question);
}
