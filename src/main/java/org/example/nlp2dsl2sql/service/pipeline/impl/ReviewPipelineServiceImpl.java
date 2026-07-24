package org.example.nlp2dsl2sql.service.pipeline.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.service.pipeline.IReviewPipelineService;
import org.example.nlp2dsl2sql.service.pipeline.ITranslationPipelineService;
import org.example.nlp2dsl2sql.tools.ReviewTool;
import org.springframework.stereotype.Service;

/**
 * SQL 审查 Pipeline Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPipelineServiceImpl implements IReviewPipelineService {

    private final ReviewTool reviewTool;
    private final ITranslationPipelineService translationPipelineService;

    /**
     * 审查 SQL。
     *
     * @param sql         待审查 SQL
     * @param enrichedDSL 富化 DSL
     * @param question    用户问题
     * @return 审查结果
     */
    @Override
    public ReviewResult review(String sql,
                               EnrichedQueryDSL enrichedDSL,
                               String question) {
        log.info("━━━ [Pipeline] REVIEW 开始 ━━━");
        String schema = translationPipelineService.buildReviewSchema(enrichedDSL);
        String reviewContext = schema + "\n用户问题: " + question;
        ReviewResult result = reviewTool.reviewSql(sql, reviewContext);
        log.info("━━━ [Pipeline] REVIEW 完成: passed={} ━━━", result.getResult());
        return result;
    }
}
