package org.example.nlp2dsl2sql.semanticdsl.metadata;

import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.*;

import java.util.List;

public interface IDslMetaDataService {
    DslEntity getEntityByCode(String entityCode);
    List<DslEntity> getEntitiesByCodes(List<String> entityCodes);
    List<DslEntity> getAllEntities();
    List<DslAttribute> getAttributesByEntityCode(String entityCode);
    List<DslRelation> getAllRelations();
    DslMetric getMetricByCode(String metricCode);
    List<DslMetric> getMetricsByCodes(List<String> metricCodes);
    List<DslMetric> getAllMetrics();
    List<DslMetricAttribute> getMetricAttributesByMetricCode(String metricCode);
    DslDimension getDimensionByCode(String dimensionCode);
    List<DslDimension> getDimensionsByCodes(List<String> dimensionCodes);
    List<DslDimension> getAllDimensions();
    List<DslDimensionValue> getDimensionValuesByCodes(List<String> dimensionCodes);
    DslMetricDimension getMetricDimension(String metricCode, String dimensionCode);
    List<DslFilter> getSystemFilters(String entityCode);
    List<DslSynonym> getAllSynonyms();
    List<DslSynonym> getSynonymsByText(String text);
    List<String> searchMetricsByVector(String vectorStr, int limit);
    List<String> searchDimensionsByVector(String vectorStr, int limit);
    List<String> searchSynonymsByVector(String vectorStr, int limit);
    List<DslSynonym> searchSynonymRowsByVector(String vectorStr, int limit);
}
