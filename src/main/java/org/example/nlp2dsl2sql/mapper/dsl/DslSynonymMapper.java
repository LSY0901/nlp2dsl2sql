package org.example.nlp2dsl2sql.mapper.dsl;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslSynonym;

import java.util.List;

@Mapper
public interface DslSynonymMapper {
    List<DslSynonym> selectAll();

    List<DslSynonym> selectBySynonymText(String synonymText);

    List<String> selectVectorSearch(@Param("vector") String vector, @Param("limit") int limit);

    List<DslSynonym> selectVectorSearchRows(@Param("vector") String vector, @Param("limit") int limit);

    List<DslSynonym> selectWithNullEmbedding();

    void updateEmbedding(@Param("id") Integer id, @Param("vector") String vector);
}
