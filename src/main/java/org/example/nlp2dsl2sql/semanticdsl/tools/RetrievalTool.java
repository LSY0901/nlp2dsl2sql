package org.example.nlp2dsl2sql.semanticdsl.tools;

import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.example.nlp2dsl2sql.semanticdsl.retriever.DslRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 多 Agent 工具：语义检索（向量召回 + 同义词扩展 + Rerank）。
 * <p>
 * 对应原 Workflow Stage 2，封装 {@link DslRetriever} 的确定性逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalTool {

    private final DslRetriever dslRetriever;

    /**
     * 执行语义检索，返回候选元数据。
     *
     * @param question 用户自然语言问题
     * @return 候选元数据 JSON（包含 metrics / dimensions / entities / dimensionValues / synonyms）
     */
    public DslCandidate retrieve(String question) {
        log.info("━━━ [Multi-Agent] RetrievalTool 启动 ━━━");
        DslCandidate candidate = dslRetriever.retrieve(question);
        log.info("━━━ [Multi-Agent] RetrievalTool 完成: metrics={}, dimensions={}, entities={} ━━━",
                candidate.getMetrics() != null ? candidate.getMetrics().size() : 0,
                candidate.getDimensions() != null ? candidate.getDimensions().size() : 0,
                candidate.getEntities() != null ? candidate.getEntities().size() : 0);
        return candidate;
    }
}
