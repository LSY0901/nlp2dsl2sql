package org.example.nlp2dsl2sql.mapper.dsl;

import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslRelation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DslRelationMapper {
    List<DslRelation> selectAll();
    List<DslRelation> selectByEntity(String entityCode);
}
