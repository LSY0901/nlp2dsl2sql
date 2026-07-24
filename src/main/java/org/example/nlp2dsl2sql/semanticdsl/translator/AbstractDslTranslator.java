package org.example.nlp2dsl2sql.semanticdsl.translator;

import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL.EnrichedJoin;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL.SelectColumn;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL.WhereColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractDslTranslator implements DslTranslator {

    @Override
    public TranslatedSql translate(EnrichedQueryDSL dsl) {
        if (dsl == null || dsl.getMainPhysicalTable() == null
                || dsl.getMainPhysicalTable().isEmpty()) {
            throw new IllegalArgumentException("富化DSL缺少主表，无法翻译SQL");
        }
        if (dsl.getSelectColumns() == null || dsl.getSelectColumns().isEmpty()) {
            throw new IllegalArgumentException("富化DSL缺少SELECT列，无法翻译SQL");
        }

        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        sql.append("SELECT ");
        sql.append(buildSelectClause(dsl.getSelectColumns()));
        sql.append(" FROM ");
        sql.append(quoteIdentifier(dsl.getMainPhysicalTable()));

        if (dsl.getJoins() != null && !dsl.getJoins().isEmpty()) {
            for (EnrichedJoin join : dsl.getJoins()) {
                sql.append(" ").append(buildJoinClause(join));
            }
        }

        List<WhereColumn> wheres = dsl.getWhereConditions();
        if (wheres != null && !wheres.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(wheres.stream()
                    .map(w -> {
                        if (w.getParameters() != null && !w.getParameters().isEmpty()) {
                            parameters.addAll(w.getParameters());
                        }
                        return w.getExpression();
                    })
                    .collect(Collectors.joining(" AND ")));
        }

        if (dsl.getGroupBy() != null && !dsl.getGroupBy().isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(dsl.getGroupBy().stream()
                    .map(this::quoteIdentifier)
                    .collect(Collectors.joining(", ")));
        }

        if (dsl.getLimit() != null && dsl.getLimit() > 0) {
            sql.append(" LIMIT ").append(dsl.getLimit());
        }

        return new TranslatedSql(sql.toString(), parameters);
    }

    protected String buildSelectClause(List<SelectColumn> columns) {
        return columns.stream()
                .map(c -> {
                    String expr = c.getExpression();
                    if (c.getAlias() != null && !c.getAlias().isEmpty()) {
                        return expr + " AS " + quoteIdentifier(c.getAlias());
                    }
                    return expr;
                })
                .collect(Collectors.joining(", "));
    }

    protected String buildJoinClause(EnrichedJoin join) {
        String joinType = join.getJoinType() != null ? join.getJoinType().trim() : "LEFT JOIN";
        if (!joinType.toUpperCase().contains("JOIN")) {
            joinType = joinType + " JOIN";
        }
        return joinType + " " + quoteIdentifier(join.getPhysicalTable())
                + " ON " + join.getOnCondition();
    }

    protected abstract String quoteIdentifier(String identifier);
}
