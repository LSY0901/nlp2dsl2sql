package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DslEntityMapper {
    List<DslEntity> selectAll();
    DslEntity selectByEntityCode(String entityCode);
    List<DslEntity> selectByEntityCodes(List<String> entityCodes);
    List<DslEntity> selectWithNullEmbedding();
    void updateEmbedding(@Param("id") Integer id, @Param("vector") String vector);
}
