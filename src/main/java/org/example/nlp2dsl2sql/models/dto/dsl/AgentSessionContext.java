package org.example.nlp2dsl2sql.models.dto.dsl;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单次 Multi-Agent 查询的会话上下文。
 * <p>
 * 通过 {@link io.agentscope.core.agent.RuntimeContext#put(Class, Object)} 注入，
 * 工具方法参数可直接接收本类型（框架自动注入，无需 {@code @ToolParam}）。
 */
@Data
public class AgentSessionContext {

    /** 意图类型名，如 METRIC_QUERY */
    private String intent;

    /** 意图识别完整结果 */
    private IntentResult intentResult;

    /** 检索候选集 */
    private DslCandidate candidate;

    /** 候选元数据文本上下文 */
    private String candidateContext;

    /** 语义 DSL */
    private SemanticQueryDSL dsl;

    /** 语义 DSL JSON */
    private String dslJson;

    /** 富化后 DSL */
    private EnrichedQueryDSL enrichedDsl;

    /** 富化后 DSL JSON */
    private String enrichedDslJson;

    /** 翻译后 SQL */
    private String sql;

    /** SQL 参数 */
    private List<Object> params;

    /** 查询结果 */
    private List<Map<String, Object>> queryResult;
}
