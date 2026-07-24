package org.example.nlp2dsl2sql.mapper.dsl;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.nlp2dsl2sql.models.entity.dsl.DslDimension;

import java.util.List;

@Mapper
public interface DslDimensionMapper {
    List<DslDimension> selectAll();

    DslDimension selectByDimensionCode(String dimensionCode);

    List<DslDimension> selectByDimensionCodes(List<String> dimensionCodes);

    List<String> selectVectorSearch(@Param("vector") String vector, @Param("limit") int limit);

    List<DslDimension> selectWithNullEmbedding();

    void updateEmbedding(@Param("id") Integer id, @Param("vector") String vector);
}
