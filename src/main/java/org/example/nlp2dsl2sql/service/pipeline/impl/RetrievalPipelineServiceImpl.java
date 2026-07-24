package org.example.nlp2dsl2sql.service.pipeline.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.tools.DslRetriever;
import org.example.nlp2dsl2sql.service.pipeline.IRetrievalPipelineService;
import org.springframework.stereotype.Service;

/**
 * 语义检索 Pipeline Service 实现（复用 DslRetriever）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalPipelineServiceImpl implements IRetrievalPipelineService {

    private final DslRetriever dslRetriever;

    /**
     * 执行语义检索。
     *
     * @param question 用户问题
     * @return 候选集
     */
    @Override
    public DslCandidate retrieve(String question) {
        log.info("━━━ [Pipeline] RETRIEVE 开始 ━━━");
        DslCandidate candidate = dslRetriever.retrieve(question);
        int metrics = candidate.getMetrics() == null ? 0 : candidate.getMetrics().size();
        int dims = candidate.getDimensions() == null ? 0 : candidate.getDimensions().size();
        log.info("━━━ [Pipeline] RETRIEVE 完成: metrics={}, dimensions={} ━━━",
                metrics, dims);
        return candidate;
    }
}
