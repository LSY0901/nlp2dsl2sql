package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslAttribute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DslAttributeMapper {
    List<DslAttribute> selectByEntityCode(String entityCode);
    List<DslAttribute> selectByEntityCodes(List<String> entityCodes);
    List<DslAttribute> selectWithNullEmbedding();
    void updateEmbedding(@Param("id") Integer id, @Param("vector") String vector);
}
