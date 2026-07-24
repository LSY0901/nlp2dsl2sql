package org.example.nlp2dsl2sql.service.pipeline;

import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;

/**
 * DSL 富化 Pipeline Service。
 */
public interface IEnrichmentPipelineService {

    /**
     * 将语义 DSL 富化为物理表/列/JOIN。
     *
     * @param dsl       语义 DSL
     * @param candidate 候选元数据
     * @return 富化后 DSL
     */
    EnrichedQueryDSL enrich(SemanticQueryDSL dsl, DslCandidate candidate);
}
