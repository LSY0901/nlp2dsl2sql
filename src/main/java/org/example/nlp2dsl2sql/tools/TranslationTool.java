package org.example.nlp2dsl2sql.tools;

import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.translator.DslTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多 Agent 工具：SQL 翻译（EnrichedQueryDSL → 参数化 SQL）。
 * <p>
 * 对应原 Workflow Stage 6，封装 {@link DslTranslator} 的确定性逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationTool {

    private final DslTranslator dslTranslator;

    /**
     * 将富化后的 DSL 翻译为参数化 SQL。
     *
     * @param enrichedDSL 富化后 DSL
     * @return 翻译结果（SQL + 参数）
     */
    public DslTranslator.TranslatedSql translate(EnrichedQueryDSL enrichedDSL) {
        log.info("━━━ [Multi-Agent] TranslationTool 启动 ━━━");
        DslTranslator.TranslatedSql translated = dslTranslator.translate(enrichedDSL);
        log.info("━━━ [Multi-Agent] TranslationTool 完成: sql={}, params={} ━━━",
                translated.sql(), translated.parameters());
        return translated;
    }

    /**
     * 构建 SQL 审查所需的 schema 摘要。
     *
     * @param dsl 富化后 DSL
     * @return schema 摘要文本
     */
    public String buildReviewSchema(EnrichedQueryDSL dsl) {
        StringBuilder sb = new StringBuilder();
        sb.append("主表: ").append(dsl.getMainPhysicalTable()).append("\n");
        if (dsl.getSelectColumns() != null) {
            sb.append("SELECT列:\n");
            dsl.getSelectColumns().forEach(c ->
                    sb.append("- ").append(c.getExpression())
                            .append(" AS ").append(c.getAlias()).append("\n"));
        }
        if (dsl.getJoins() != null) {
            sb.append("JOIN:\n");
            dsl.getJoins().forEach(j ->
                    sb.append("- ").append(j.getJoinType()).append(" ")
                            .append(j.getPhysicalTable()).append(" ON ")
                            .append(j.getOnCondition()).append("\n"));
        }
        if (dsl.getWhereConditions() != null) {
            sb.append("WHERE:\n");
            dsl.getWhereConditions().forEach(w ->
                    sb.append("- ").append(w.getExpression()).append("\n"));
        }
        return sb.toString();
    }

    /**
     * 获取翻译后的 SQL 文本（用于传递给审查 Agent 和执行工具）。
     *
     * @param translated 翻译结果
     * @return SQL 字符串
     */
    public String extractSql(DslTranslator.TranslatedSql translated) {
        return translated.sql();
    }

    /**
     * 获取翻译后的参数列表（用于传递给执行工具）。
     *
     * @param translated 翻译结果
     * @return 参数列表
     */
    public List<Object> extractParams(DslTranslator.TranslatedSql translated) {
        return translated.parameters();
    }
}
