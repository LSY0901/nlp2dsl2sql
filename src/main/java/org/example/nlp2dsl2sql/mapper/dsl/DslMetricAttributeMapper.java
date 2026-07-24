package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.models.entity.dsl.DslMetricAttribute;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DslMetricAttributeMapper {
    List<DslMetricAttribute> selectByMetricCode(String metricCode);
    List<DslMetricAttribute> selectByMetricCodes(List<String> metricCodes);
}
