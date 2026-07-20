package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DslMetricMapper {
    List<DslMetric> selectAll();
    DslMetric selectByMetricCode(String metricCode);
    List<DslMetric> selectByMetricCodes(List<String> metricCodes);
    List<String> selectVectorSearch(@Param("vector") String vector, @Param("limit") int limit);
    List<DslMetric> selectWithNullEmbedding();
    void updateEmbedding(@Param("id") Integer id, @Param("vector") String vector);
}
