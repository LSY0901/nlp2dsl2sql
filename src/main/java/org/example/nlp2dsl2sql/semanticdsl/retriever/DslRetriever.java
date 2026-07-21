package org.example.nlp2dsl2sql.semanticdsl.retriever;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.EmbeddingClient;
import org.example.nlp2dsl2sql.semanticdsl.metadata.IDslMetaDataService;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.*;
import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 语义层检索：向量召回 + 同义词扩展 + Rerank。
 * 改造：用 EmbeddingClient 替代 Spring AI EmbeddingModel。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DslRetriever {

    private final IDslMetaDataService metaDataService;
    private final EmbeddingClient embeddingClient;
    private final RerankClient rerankClient;

    public DslCandidate retrieve(String question) {
        log.info("━━━ DSL Retriever 启动 ━━━");

        float[] questionVector = embeddingClient.embed(question);
        String vectorStr = EmbeddingClient.toVectorStr(questionVector);

        int recallTopN = 10;
        //语义识别匹配
        List<String> metricCodes = new ArrayList<>(
                metaDataService.searchMetricsByVector(vectorStr, recallTopN));
        //查询的维度匹配
        List<String> dimensionCodes = new ArrayList<>(
                metaDataService.searchDimensionsByVector(vectorStr, recallTopN));
        //业务同义词匹配
        List<DslSynonym> hitSynonyms =
                metaDataService.searchSynonymRowsByVector(vectorStr, recallTopN);
        log.info("向量召回: metrics={}, dimensions={}, synonyms={}",
                metricCodes.size(), dimensionCodes.size(), hitSynonyms.size());

        //维度和语义扩展 根据业务同义词扩展
        expandBySynonyms(question, hitSynonyms, metricCodes, dimensionCodes);


        //多语义匹配
        List<DslMetric> candidateMetrics = metaDataService.getMetricsByCodes(metricCodes);
        //多维度匹配
        List<DslDimension> candidateDimensions =
                metaDataService.getDimensionsByCodes(dimensionCodes);

        //语义reranker
        List<DslMetric> rerankedMetrics = rerankMetrics(question, candidateMetrics);
        //维度reranker
        List<DslDimension> rerankedDimensions = rerankDimensions(question, candidateDimensions);

        if (isCountQuestion(question)) {
            rerankedMetrics = boostCountMetrics(rerankedMetrics);
        }

        int topK = 3;
        //取top3
        List<DslMetric> topMetrics = rerankedMetrics.stream()
                .limit(topK).collect(Collectors.toList());
        //取top6
        List<DslDimension> topDimensions = rerankedDimensions.stream()
                .limit(topK * 2L).collect(Collectors.toList());

        log.info("精排后候选指标: {}", topMetrics.stream()
                .map(DslMetric::getMetricCode).collect(Collectors.toList()));

        Set<String> entityCodes = topMetrics.stream()
                .map(DslMetric::getEntityCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (DslSynonym s : hitSynonyms) {
            if ("ENTITY".equalsIgnoreCase(s.getObjectType()) && s.getObjectCode() != null) {
                entityCodes.add(s.getObjectCode());
            }
        }
        for (DslDimension d : topDimensions) {
            if (d.getEntityCode() != null) {
                entityCodes.add(d.getEntityCode());
            }
        }
        //获取所有关系
        List<DslRelation> relations = metaDataService.getAllRelations();

        entityCodes.addAll(collectBridgeEntities(entityCodes, relations));

        List<DslEntity> entities =
                metaDataService.getEntitiesByCodes(new ArrayList<>(entityCodes));
        List<DslFilter> systemFilters = entities.stream()
                .flatMap(e -> metaDataService.getSystemFilters(e.getEntityCode()).stream())
                .collect(Collectors.toList());
        List<DslAttribute> attributes = entities.stream()
                .flatMap(e -> metaDataService.getAttributesByEntityCode(e.getEntityCode()).stream())
                .collect(Collectors.toList());
        List<DslDimensionValue> dimensionValues = metaDataService.getDimensionValuesByCodes(
                topDimensions.stream()
                        .map(DslDimension::getDimensionCode)
                        .collect(Collectors.toList()));

        DslCandidate candidate = new DslCandidate();
        candidate.setMetrics(topMetrics);
        candidate.setEntities(entities);
        candidate.setDimensions(topDimensions);
        candidate.setDimensionValues(dimensionValues);
        candidate.setSynonyms(hitSynonyms);
        candidate.setRelations(relations);
        candidate.setSystemFilters(systemFilters);
        candidate.setAttributes(attributes);

        log.info("━━━ DSL Retriever 完成: metrics={}, dimensions={}, entities={} ━━━",
                topMetrics.size(), topDimensions.size(), entities.size());
        return candidate;
    }

    private Set<String> collectBridgeEntities(Set<String> entityCodes,
                                              List<DslRelation> relations) {
        Set<String> bridges = new LinkedHashSet<>();
        List<String> list = new ArrayList<>(entityCodes);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                List<String> pathNodes = findEntityPathNodes(list.get(i), list.get(j), relations);
                bridges.addAll(pathNodes);
            }
        }
        bridges.removeAll(entityCodes);
        return bridges;
    }

    private List<String> findEntityPathNodes(String from, String to,
                                             List<DslRelation> relations) {
        if (from.equals(to)) {
            return List.of(from);
        }
        Map<String, List<String>> graph = new HashMap<>();
        for (DslRelation r : relations) {
            graph.computeIfAbsent(r.getSourceEntity(), k -> new ArrayList<>()).add(r.getTargetEntity());
            graph.computeIfAbsent(r.getTargetEntity(), k -> new ArrayList<>()).add(r.getSourceEntity());
        }
        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> prev = new HashMap<>();
        queue.add(from);
        prev.put(from, null);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur.equals(to)) {
                break;
            }
            for (String next : graph.getOrDefault(cur, List.of())) {
                if (prev.containsKey(next)) {
                    continue;
                }
                prev.put(next, cur);
                queue.add(next);
            }
        }
        if (!prev.containsKey(to)) {
            return List.of();
        }
        List<String> path = new ArrayList<>();
        String cur = to;
        while (cur != null) {
            path.add(0, cur);
            cur = prev.get(cur);
        }
        return path;
    }

    private void expandBySynonyms(String question,
                                  List<DslSynonym> hitSynonyms,
                                  List<String> metricCodes,
                                  List<String> dimensionCodes) {
        if (hitSynonyms == null || hitSynonyms.isEmpty()) {
            return;
        }
        Set<String> metricSet = new LinkedHashSet<>(metricCodes);
        Set<String> dimensionSet = new LinkedHashSet<>(dimensionCodes);
        Set<String> entityCodes = new LinkedHashSet<>();

        for (DslSynonym s : hitSynonyms) {
            if (s.getObjectCode() == null || s.getObjectType() == null) {
                continue;
            }
            String type = s.getObjectType().trim().toUpperCase();
            switch (type) {
                //语义
                case "METRIC" -> metricSet.add(s.getObjectCode());
                //维度
                case "DIMENSION" -> dimensionSet.add(s.getObjectCode());
                //实体
                case "ENTITY" -> entityCodes.add(s.getObjectCode());
                default -> log.debug("忽略未知同义词类型: {}", type);
            }
        }

        if (!entityCodes.isEmpty()) {
            for (DslMetric m : metaDataService.getAllMetrics()) {
                if (entityCodes.contains(m.getEntityCode())) {
                    metricSet.add(m.getMetricCode());
                }
            }
            log.info("同义词实体扩展: entities={}, 追加后metrics={}",
                    entityCodes, metricSet.size());
        }

        metricCodes.clear();
        metricCodes.addAll(metricSet);
        dimensionCodes.clear();
        dimensionCodes.addAll(dimensionSet);
        log.info("同义词扩展后: metrics={}, dimensions={}, question={}",
                metricCodes.size(), dimensionCodes.size(), question);
    }

    private boolean isCountQuestion(String question) {
        if (question == null) {
            return false;
        }
        return question.contains("多少")
                || question.contains("几个")
                || question.contains("数量")
                || question.contains("人数")
                || question.contains("总数");
    }

    private List<DslMetric> boostCountMetrics(List<DslMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return metrics;
        }
        List<DslMetric> boosted = new ArrayList<>();
        List<DslMetric> others = new ArrayList<>();
        for (DslMetric m : metrics) {
            if (isCountMetric(m)) {
                boosted.add(m);
            } else {
                others.add(m);
            }
        }
        boosted.addAll(others);
        return boosted;
    }

    private boolean isCountMetric(DslMetric m) {
        String name = (m.getMetricName() == null ? "" : m.getMetricName())
                + (m.getMetricCode() == null ? "" : m.getMetricCode())
                + (m.getDescription() == null ? "" : m.getDescription());
        String lower = name.toLowerCase();
        return lower.contains("数量")
                || lower.contains("人数")
                || lower.contains("count")
                || lower.contains("统计");
    }

    private List<DslMetric> rerankMetrics(String question, List<DslMetric> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> documents = candidates.stream()
                .map(m -> m.getMetricName() + " "
                        + (m.getDescription() != null ? m.getDescription() : ""))
                .collect(Collectors.toList());
        List<Double> scores = rerankClient.rerank(question, documents);
        return sortByScore(candidates, scores);
    }

    private List<DslDimension> rerankDimensions(String question, List<DslDimension> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> documents = candidates.stream()
                .map(d -> d.getDimensionName() + " "
                        + (d.getDescription() != null ? d.getDescription() : ""))
                .collect(Collectors.toList());
        List<Double> scores = rerankClient.rerank(question, documents);
        return sortByScore(candidates, scores);
    }

    private <T> List<T> sortByScore(List<T> items, List<Double> scores) {
        if (scores == null || scores.size() != items.size()) {
            return items;
        }
        List<int[]> indexed = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            indexed.add(new int[]{i, i});
        }
        indexed.sort((a, b) -> Double.compare(scores.get(b[0]), scores.get(a[0])));
        return indexed.stream().map(idx -> items.get(idx[0])).collect(Collectors.toList());
    }

    /**
     * Rerank 服务客户端。
     */
    @Slf4j
    @Component
    public static class RerankClient {
        private final RestClient rerankClient;

        public RerankClient(
                @org.springframework.beans.factory.annotation.Value(
                        "${rerank.base-url:http://localhost:8083}") String baseUrl) {
            this.rerankClient = RestClient.builder().baseUrl(baseUrl).build();
        }

        @SuppressWarnings("unchecked")
        public List<Double> rerank(String query, List<String> documents) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("query", query);
                body.put("documents", documents);
                Map<String, Object> resp = rerankClient.post()
                        .uri("/rerank")
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                if (resp != null && resp.containsKey("scores")) {
                    return ((List<Number>) resp.get("scores")).stream()
                            .map(Number::doubleValue)
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("Rerank 调用失败，跳过精排: {}", e.getMessage());
            }
            return documents.stream().map(d -> 0.0).collect(Collectors.toList());
        }
    }
}
