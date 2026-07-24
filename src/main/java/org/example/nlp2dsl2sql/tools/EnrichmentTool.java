package org.example.nlp2dsl2sql.tools;

import org.example.nlp2dsl2sql.semanticdsl.enricher.SemanticDslEnricher;
import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 多 Agent 工具：DSL 富化（语义 code → 物理表/列 + BFS JOIN 路径求解）。
 * <p>
 * 对应原 Workflow Stage 5，封装 {@link SemanticDslEnricher} 的确定性逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrichmentTool {

    private final SemanticDslEnricher dslEnricher;

    /**
     * 将语义 DSL 中的 code 转换为物理表/列，求解 JOIN 路径，注入系统过滤。
     *
     * @param semanticDSL 语义 DSL
     * @param candidate   检索候选集
     * @return 富化后 DSL
     */
    public EnrichedQueryDSL enrich(SemanticQueryDSL semanticDSL, DslCandidate candidate) {
        log.info("━━━ [Multi-Agent] EnrichmentTool 启动 ━━━");
        EnrichedQueryDSL enriched = dslEnricher.enrich(semanticDSL, candidate);
        log.info("━━━ [Multi-Agent] EnrichmentTool 完成: table={}, selectCols={}, joins={}, wheres={} ━━━",
                enriched.getMainPhysicalTable(),
                enriched.getSelectColumns() != null ? enriched.getSelectColumns().size() : 0,
                enriched.getJoins() != null ? enriched.getJoins().size() : 0,
                enriched.getWhereConditions() != null ? enriched.getWhereConditions().size() : 0);
        return enriched;
    }
}
