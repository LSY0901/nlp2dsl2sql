package org.example.nlp2dsl2sql.semanticdsl.enricher;

import org.example.nlp2dsl2sql.semanticdsl.metadata.IDslMetaDataService;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslDimension;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslDimensionValue;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslEntity;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslFilter;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslMetric;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslRelation;
import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.example.nlp2dsl2sql.semanticdsl.model.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticFilter;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticDslEnricher {

    private final IDslMetaDataService metaDataService;

    public EnrichedQueryDSL enrich(SemanticQueryDSL semanticDSL, DslCandidate candidate) {
        log.info("━━━ DSL Enricher 启动 ━━━");

        EnrichedQueryDSL enriched = new EnrichedQueryDSL();
        enriched.setLimit(1000);

        DslEntity mainEntity = resolveMainEntity(semanticDSL, candidate);
        if (mainEntity == null) {
            log.warn("未找到主实体，返回空EnrichedQueryDSL");
            return enriched;
        }

        enriched.setMainEntity(mainEntity.getEntityCode());
        enriched.setMainPhysicalTable(mainEntity.getPhysicalTable());
        enriched.setMainPrimaryKey(mainEntity.getPrimaryKey());

        List<EnrichedQueryDSL.SelectColumn> selectColumns = new ArrayList<>();
        List<EnrichedQueryDSL.EnrichedJoin> joins = new ArrayList<>();
        List<EnrichedQueryDSL.WhereColumn> whereConditions = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        Set<String> joinedEntities = new HashSet<>();
        joinedEntities.add(mainEntity.getEntityCode());

        if (semanticDSL.getMetric() != null && !semanticDSL.getMetric().isEmpty()) {
            DslMetric metric = findMetric(semanticDSL.getMetric(), candidate);
            if (metric != null) {
                EnrichedQueryDSL.SelectColumn col = new EnrichedQueryDSL.SelectColumn();
                col.setExpression(buildMetricExpression(metric));
                col.setAlias(metric.getMetricCode());
                selectColumns.add(col);
            }
        }

        if (semanticDSL.getDimensions() != null) {
            for (String dimCode : semanticDSL.getDimensions()) {
                DslDimension dim = findDimension(dimCode, candidate);
                if (dim == null) {
                    continue;
                }
                String qualifiedCol = qualifyColumn(dim, candidate);
                EnrichedQueryDSL.SelectColumn col = new EnrichedQueryDSL.SelectColumn();
                col.setExpression(qualifiedCol);
                col.setAlias(dim.getDimensionCode());
                selectColumns.add(col);
                groupBy.add(qualifiedCol);
                ensureJoinsForEntity(dim.getEntityCode(), mainEntity, candidate,
                        joins, joinedEntities);
            }
        }
        enriched.setGroupBy(groupBy);

        if (semanticDSL.getFilters() != null && !semanticDSL.getFilters().isEmpty()) {
            Map<String, List<SemanticFilter>> grouped = new LinkedHashMap<>();
            for (SemanticFilter filter : semanticDSL.getFilters()) {
                grouped.computeIfAbsent(filter.getDimension(), k -> new ArrayList<>()).add(filter);
            }
            for (Map.Entry<String, List<SemanticFilter>> entry : grouped.entrySet()) {
                String dimCode = entry.getKey();
                List<SemanticFilter> filters = entry.getValue();
                DslDimension dim = findDimension(dimCode, candidate);
                if (dim == null) {
                    log.warn("过滤维度不在候选中: {}", dimCode);
                    continue;
                }
                ensureJoinsForEntity(dim.getEntityCode(), mainEntity, candidate,
                        joins, joinedEntities);

                List<Object> params = new ArrayList<>();
                for (SemanticFilter filter : filters) {
                    params.add(resolvePhysicalValue(dimCode, filter.getValue(), candidate));
                }
                EnrichedQueryDSL.WhereColumn where = new EnrichedQueryDSL.WhereColumn();
                String col = qualifyColumn(dim, candidate);
                if (params.size() == 1) {
                    where.setExpression(col + " = ?");
                } else {
                    String placeholders = String.join(", ",
                            Collections.nCopies(params.size(), "?"));
                    where.setExpression(col + " IN (" + placeholders + ")");
                }
                where.setParameters(params);
                where.setSystemFilter(false);
                whereConditions.add(where);
            }
        }

        if (candidate.getSystemFilters() != null) {
            for (DslFilter sysFilter : candidate.getSystemFilters()) {
                if (!mainEntity.getEntityCode().equals(sysFilter.getEntityCode())
                        || !Boolean.TRUE.equals(sysFilter.getIsSystem())) {
                    continue;
                }
                if (isTautologyFilter(sysFilter.getExpression())) {
                    log.info("跳过无意义系统过滤: {}", sysFilter.getExpression());
                    continue;
                }
                EnrichedQueryDSL.WhereColumn where = new EnrichedQueryDSL.WhereColumn();
                where.setExpression(sysFilter.getExpression());
                where.setSystemFilter(true);
                whereConditions.add(where);
            }
        }

        enriched.setSelectColumns(selectColumns);
        enriched.setJoins(joins);
        enriched.setWhereConditions(whereConditions);

        log.info("━━━ DSL Enricher 完成: selectCols={}, joins={}, wheres={} ━━━",
                selectColumns.size(), joins.size(), whereConditions.size());
        return enriched;
    }

    private String buildMetricExpression(DslMetric metric) {
        String expr = metric.getExpression() == null ? "" : metric.getExpression().trim();
        String agg = metric.getAggregationType();
        if (agg == null || agg.isBlank()) {
            return expr;
        }
        if (alreadyAggregated(expr)) {
            return expr;
        }
        return agg.trim() + "(" + expr + ")";
    }

    private boolean alreadyAggregated(String expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        String upper = expr.toUpperCase();
        return upper.startsWith("COUNT(")
                || upper.startsWith("SUM(")
                || upper.startsWith("AVG(")
                || upper.startsWith("MAX(")
                || upper.startsWith("MIN(");
    }

    private boolean isTautologyFilter(String expression) {
        if (expression == null) {
            return false;
        }
        return expression.toUpperCase().matches(".*\\bIS\\s+NOT\\s+NULL\\s*$");
    }

    private DslEntity resolveMainEntity(SemanticQueryDSL semanticDSL, DslCandidate candidate) {
        if (semanticDSL.getEntity() != null) {
            DslEntity fromCandidate = findEntity(semanticDSL.getEntity(), candidate);
            if (fromCandidate != null) {
                return fromCandidate;
            }
            return metaDataService.getEntityByCode(semanticDSL.getEntity());
        }
        if (candidate.getEntities() != null && !candidate.getEntities().isEmpty()) {
            return candidate.getEntities().get(0);
        }
        return null;
    }

    private void ensureJoinsForEntity(String targetEntityCode,
                                      DslEntity mainEntity,
                                      DslCandidate candidate,
                                      List<EnrichedQueryDSL.EnrichedJoin> joins,
                                      Set<String> joinedEntities) {
        if (targetEntityCode == null
                || targetEntityCode.equals(mainEntity.getEntityCode())
                || joinedEntities.contains(targetEntityCode)) {
            return;
        }

        List<DslRelation> relations = candidate.getRelations() != null
                ? candidate.getRelations() : metaDataService.getAllRelations();
        List<DslRelation> path = findRelationPath(
                mainEntity.getEntityCode(), targetEntityCode, relations);
        if (path.isEmpty()) {
            log.warn("无法找到 JOIN 路径: {} -> {}", mainEntity.getEntityCode(), targetEntityCode);
            return;
        }

        String current = mainEntity.getEntityCode();
        for (DslRelation relation : path) {
            String next = relation.getSourceEntity().equals(current)
                    ? relation.getTargetEntity() : relation.getSourceEntity();
            if (!joinedEntities.contains(next)) {
                DslEntity target = findEntity(next, candidate);
                if (target == null) {
                    target = metaDataService.getEntityByCode(next);
                }
                if (target == null) {
                    log.warn("JOIN 目标实体不存在: {}", next);
                    return;
                }
                EnrichedQueryDSL.EnrichedJoin join = new EnrichedQueryDSL.EnrichedJoin();
                join.setJoinType(normalizeJoinType(relation.getJoinType()));
                join.setPhysicalTable(target.getPhysicalTable());
                join.setOnCondition(relation.getJoinCondition());
                join.setSourceRelation(relation);
                joins.add(join);
                joinedEntities.add(next);
            }
            current = next;
        }
    }

    private List<DslRelation> findRelationPath(String from, String to,
                                               List<DslRelation> relations) {
        if (from.equals(to)) {
            return List.of();
        }
        Map<String, List<DslRelation>> graph = new HashMap<>();
        for (DslRelation r : relations) {
            if (Boolean.TRUE.equals(r.getIsDeleted())) {
                continue;
            }
            graph.computeIfAbsent(r.getSourceEntity(), k -> new ArrayList<>()).add(r);
            graph.computeIfAbsent(r.getTargetEntity(), k -> new ArrayList<>()).add(r);
        }

        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> prevNode = new HashMap<>();
        Map<String, DslRelation> prevEdge = new HashMap<>();
        queue.add(from);
        prevNode.put(from, null);

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur.equals(to)) {
                break;
            }
            for (DslRelation r : graph.getOrDefault(cur, List.of())) {
                String next = r.getSourceEntity().equals(cur)
                        ? r.getTargetEntity() : r.getSourceEntity();
                if (prevNode.containsKey(next)) {
                    continue;
                }
                prevNode.put(next, cur);
                prevEdge.put(next, r);
                queue.add(next);
            }
        }

        if (!prevNode.containsKey(to)) {
            return List.of();
        }
        List<DslRelation> path = new ArrayList<>();
        String cur = to;
        while (prevEdge.containsKey(cur)) {
            path.add(0, prevEdge.get(cur));
            cur = prevNode.get(cur);
        }
        return path;
    }

    private String normalizeJoinType(String joinType) {
        if (joinType == null || joinType.isBlank()) {
            return "LEFT JOIN";
        }
        String trimmed = joinType.trim();
        if (trimmed.toUpperCase().contains("JOIN")) {
            return trimmed;
        }
        return trimmed + " JOIN";
    }

    private String qualifyColumn(DslDimension dim, DslCandidate candidate) {
        String col = dim.getPhysicalColumn();
        if (col == null) {
            return null;
        }
        if (col.contains(".")) {
            return col;
        }
        DslEntity entity = findEntity(dim.getEntityCode(), candidate);
        if (entity == null) {
            entity = metaDataService.getEntityByCode(dim.getEntityCode());
        }
        if (entity != null && entity.getPhysicalTable() != null) {
            return entity.getPhysicalTable() + "." + col;
        }
        return col;
    }

    private String resolvePhysicalValue(String dimensionCode, String valueCode,
                                        DslCandidate candidate) {
        List<DslDimensionValue> values = candidate.getDimensionValues();
        if (values == null || values.isEmpty()) {
            values = metaDataService.getDimensionValuesByCodes(List.of(dimensionCode));
        }
        final List<DslDimensionValue> lookup = values == null ? List.of() : values;

        return lookup.stream()
                .filter(v -> dimensionCode.equals(v.getDimensionCode())
                        && valueCode.equals(v.getValueCode()))
                .map(DslDimensionValue::getPhysicalValue)
                .findFirst()
                .or(() -> lookup.stream()
                        .filter(v -> dimensionCode.equals(v.getDimensionCode())
                                && (valueCode.equals(v.getValueName())
                                || valueCode.equals(v.getPhysicalValue())))
                        .map(DslDimensionValue::getPhysicalValue)
                        .findFirst())
                .orElseGet(() -> {
                    log.warn("维度值未在元数据中找到，回退原值: dim={}, value={}",
                            dimensionCode, valueCode);
                    return valueCode;
                });
    }

    private DslMetric findMetric(String code, DslCandidate candidate) {
        if (candidate.getMetrics() == null) {
            return metaDataService.getMetricByCode(code);
        }
        return candidate.getMetrics().stream()
                .filter(m -> code.equals(m.getMetricCode()))
                .findFirst()
                .orElseGet(() -> metaDataService.getMetricByCode(code));
    }

    private DslDimension findDimension(String code, DslCandidate candidate) {
        if (candidate.getDimensions() != null) {
            DslDimension found = candidate.getDimensions().stream()
                    .filter(d -> code.equals(d.getDimensionCode()))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                return found;
            }
        }
        return metaDataService.getDimensionByCode(code);
    }

    private DslEntity findEntity(String code, DslCandidate candidate) {
        if (candidate.getEntities() == null) {
            return null;
        }
        return candidate.getEntities().stream()
                .filter(e -> code.equals(e.getEntityCode()))
                .findFirst()
                .orElse(null);
    }
}
