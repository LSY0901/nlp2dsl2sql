package org.example.nlp2dsl2sql.service.pipeline;

import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.translator.DslTranslator;

/**
 * SQL 翻译 Pipeline Service。
 */
public interface ITranslationPipelineService {

    /**
     * 将富化 DSL 翻译为参数化 SQL。
     *
     * @param enrichedDSL 富化后 DSL
     * @return SQL 与参数
     */
    DslTranslator.TranslatedSql translate(EnrichedQueryDSL enrichedDSL);

    /**
     * 构建审查用 schema 摘要。
     *
     * @param enrichedDSL 富化后 DSL
     * @return schema 文本
     */
    String buildReviewSchema(EnrichedQueryDSL enrichedDSL);
}
