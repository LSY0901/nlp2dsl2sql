package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslMetricDimension;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DslMetricDimensionMapper {
    DslMetricDimension selectByMetricAndDimension(@Param("metricCode") String metricCode, @Param("dimensionCode") String dimensionCode);
    List<DslMetricDimension> selectByMetricCode(String metricCode);
}
