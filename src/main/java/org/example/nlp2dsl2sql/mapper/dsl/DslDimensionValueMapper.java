package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.models.entity.dsl.DslDimensionValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DslDimensionValueMapper {
    List<DslDimensionValue> selectByDimensionCode(String dimensionCode);
    List<DslDimensionValue> selectByDimensionCodes(List<String> dimensionCodes);
    List<DslDimensionValue> selectWithNullEmbedding();
    void updateEmbedding(@Param("id") Integer id, @Param("vector") String vector);
}
