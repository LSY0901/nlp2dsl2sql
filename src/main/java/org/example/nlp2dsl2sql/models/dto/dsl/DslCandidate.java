package org.example.nlp2dsl2sql.models.dto.dsl;

import lombok.Data;
import org.example.nlp2dsl2sql.models.entity.dsl.*;

import java.util.List;

@Data
public class DslCandidate {
    private List<DslMetric> metrics;
    private List<DslEntity> entities;
    private List<DslDimension> dimensions;
    private List<DslDimensionValue> dimensionValues;
    private List<DslSynonym> synonyms;
    private List<DslRelation> relations;
    private List<DslFilter> systemFilters;
    private List<DslAttribute> attributes;
}
