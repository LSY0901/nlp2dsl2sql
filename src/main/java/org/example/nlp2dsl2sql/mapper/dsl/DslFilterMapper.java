package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.models.entity.dsl.DslFilter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DslFilterMapper {
    List<DslFilter> selectSystemFiltersByEntityCode(String entityCode);
    List<DslFilter> selectByEntityCode(String entityCode);
    List<DslFilter> selectWithNullEmbedding();
    void updateEmbedding(@Param("id") Integer id, @Param("vector") String vector);
}
