package org.example.nlp2dsl2sql.service.pipeline.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.semanticdsl.enricher.SemanticDslEnricher;
import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.service.pipeline.IEnrichmentPipelineService;
import org.springframework.stereotype.Service;

/**
 * DSL 富化 Pipeline Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentPipelineServiceImpl implements IEnrichmentPipelineService {

    private final SemanticDslEnricher dslEnricher;

    /**
     * 富化语义 DSL。
     *
     * @param dsl       语义 DSL
     * @param candidate 候选元数据
     * @return 富化后 DSL
     */
    @Override
    public EnrichedQueryDSL enrich(SemanticQueryDSL dsl, DslCandidate candidate) {
        log.info("━━━ [Pipeline] ENRICH 开始 ━━━");
        EnrichedQueryDSL enriched = dslEnricher.enrich(dsl, candidate);
        log.info("━━━ [Pipeline] ENRICH 完成: mainTable={} ━━━",
                enriched.getMainPhysicalTable());
        return enriched;
    }
}
