package org.example.nlp2dsl2sql.semanticdsl.metadata;

import org.example.nlp2dsl2sql.mapper.dsl.*;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DslMetaDataServiceImpl implements IDslMetaDataService {

    private final DslEntityMapper dslEntityMapper;
    private final DslAttributeMapper dslAttributeMapper;
    private final DslRelationMapper dslRelationMapper;
    private final DslMetricMapper dslMetricMapper;
    private final DslMetricAttributeMapper dslMetricAttributeMapper;
    private final DslDimensionMapper dslDimensionMapper;
    private final DslDimensionValueMapper dslDimensionValueMapper;
    private final DslMetricDimensionMapper dslMetricDimensionMapper;
    private final DslFilterMapper dslFilterMapper;
    private final DslSynonymMapper dslSynonymMapper;

    @Override
    public DslEntity getEntityByCode(String entityCode) {
        return dslEntityMapper.selectByEntityCode(entityCode);
    }

    @Override
    public List<DslEntity> getEntitiesByCodes(List<String> c) {
        return c == null || c.isEmpty() ? Collections.emptyList() : dslEntityMapper.selectByEntityCodes(c);
    }

    @Override
    public List<DslEntity> getAllEntities() {
        return dslEntityMapper.selectAll();
    }

    @Override
    public List<DslAttribute> getAttributesByEntityCode(String entityCode) {
        return dslAttributeMapper.selectByEntityCode(entityCode);
    }

    @Override
    public List<DslRelation> getAllRelations() {
        return dslRelationMapper.selectAll();
    }

    @Override
    public DslMetric getMetricByCode(String metricCode) {
        return dslMetricMapper.selectByMetricCode(metricCode);
    }

    @Override
    public List<DslMetric> getMetricsByCodes(List<String> c) {
        return c == null || c.isEmpty() ? Collections.emptyList() : dslMetricMapper.selectByMetricCodes(c);
    }

    @Override
    public List<DslMetric> getAllMetrics() {
        return dslMetricMapper.selectAll();
    }

    @Override
    public List<DslMetricAttribute> getMetricAttributesByMetricCode(String metricCode) {
        return dslMetricAttributeMapper.selectByMetricCode(metricCode);
    }

    @Override
    public DslDimension getDimensionByCode(String dimensionCode) {
        return dslDimensionMapper.selectByDimensionCode(dimensionCode);
    }

    @Override
    public List<DslDimension> getDimensionsByCodes(List<String> c) {
        return c == null || c.isEmpty() ? Collections.emptyList() : dslDimensionMapper.selectByDimensionCodes(c);
    }

    @Override
    public List<DslDimension> getAllDimensions() {
        return dslDimensionMapper.selectAll();
    }

    @Override
    public List<DslDimensionValue> getDimensionValuesByCodes(List<String> c) {
        return c == null || c.isEmpty() ? Collections.emptyList() : dslDimensionValueMapper.selectByDimensionCodes(c);
    }

    @Override
    public DslMetricDimension getMetricDimension(String m, String d) {
        return dslMetricDimensionMapper.selectByMetricAndDimension(m, d);
    }

    @Override
    public List<DslFilter> getSystemFilters(String entityCode) {
        return dslFilterMapper.selectSystemFiltersByEntityCode(entityCode);
    }

    @Override
    public List<DslSynonym> getAllSynonyms() {
        return dslSynonymMapper.selectAll();
    }

    @Override
    public List<DslSynonym> getSynonymsByText(String text) {
        return dslSynonymMapper.selectBySynonymText(text);
    }

    @Override
    public List<String> searchMetricsByVector(String v, int l) {
        return dslMetricMapper.selectVectorSearch(v, l);
    }

    @Override
    public List<String> searchDimensionsByVector(String v, int l) {
        return dslDimensionMapper.selectVectorSearch(v, l);
    }

    @Override
    public List<String> searchSynonymsByVector(String v, int l) {
        return dslSynonymMapper.selectVectorSearch(v, l);
    }

    @Override
    public List<DslSynonym> searchSynonymRowsByVector(String v, int l) {
        return dslSynonymMapper.selectVectorSearchRows(v, l);
    }
}
