package org.example.nlp2dsl2sql.service.pipeline;

import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;

/**
 * 语义检索 Pipeline Service。
 */
public interface IRetrievalPipelineService {

    /**
     * 根据用户问题检索候选元数据。
     *
     * @param question 用户自然语言问题
     * @return 候选集
     */
    DslCandidate retrieve(String question);
}
