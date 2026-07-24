package org.example.nlp2dsl2sql.service.pipeline.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.translator.DslTranslator;
import org.example.nlp2dsl2sql.service.pipeline.ITranslationPipelineService;
import org.springframework.stereotype.Service;

/**
 * SQL 翻译 Pipeline Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationPipelineServiceImpl implements ITranslationPipelineService {

    private final DslTranslator dslTranslator;

    /**
     * 翻译为参数化 SQL。
     *
     * @param enrichedDSL 富化后 DSL
     * @return SQL 与参数
     */
    @Override
    public DslTranslator.TranslatedSql translate(EnrichedQueryDSL enrichedDSL) {
        log.info("━━━ [Pipeline] TRANSLATE 开始 ━━━");
        DslTranslator.TranslatedSql translated = dslTranslator.translate(enrichedDSL);
        log.info("━━━ [Pipeline] TRANSLATE 完成: sql={} ━━━", translated.sql());
        return translated;
    }

    /**
     * 构建审查用 schema 摘要。
     *
     * @param enrichedDSL 富化后 DSL
     * @return schema 文本
     */
    @Override
    public String buildReviewSchema(EnrichedQueryDSL enrichedDSL) {
        StringBuilder sb = new StringBuilder();
        sb.append("主表: ").append(enrichedDSL.getMainPhysicalTable()).append("\n");
        if (enrichedDSL.getSelectColumns() != null) {
            sb.append("SELECT列:\n");
            enrichedDSL.getSelectColumns().forEach(c ->
                    sb.append("- ").append(c.getExpression())
                            .append(" AS ").append(c.getAlias()).append("\n"));
        }
        if (enrichedDSL.getJoins() != null) {
            sb.append("JOIN:\n");
            enrichedDSL.getJoins().forEach(j ->
                    sb.append("- ").append(j.getJoinType()).append(" ")
                            .append(j.getPhysicalTable()).append(" ON ")
                            .append(j.getOnCondition()).append("\n"));
        }
        if (enrichedDSL.getWhereConditions() != null) {
            sb.append("WHERE:\n");
            enrichedDSL.getWhereConditions().forEach(w ->
                    sb.append("- ").append(w.getExpression()).append("\n"));
        }
        return sb.toString();
    }
}
