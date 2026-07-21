package org.example.nlp2dsl2sql.semanticdsl.tools;

import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.example.nlp2dsl2sql.semanticdsl.model.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.model.IntentResult;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import org.example.nlp2dsl2sql.tools.ReviewTool;
import com.alibaba.fastjson2.JSON;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Supervisor Agent 业务工具集。
 * <p>
 * 通过 {@link Tool} / {@link ToolParam} 注解注册到 AgentScope {@code Toolkit}，
 * 由 {@link io.agentscope.harness.agent.HarnessAgent} 在 ReAct 循环中自动调用。
 * <p>
 * 跨工具中间状态存放在 {@link MultiAgentSessionContext}，由
 * {@link io.agentscope.core.agent.RuntimeContext} 按次注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiAgentToolRegistry {

    private final RetrievalTool retrievalTool;
    private final CandidateContextTool candidateContextTool;
    private final ValidationTool validationTool;
    private final EnrichmentTool enrichmentTool;
    private final TranslationTool translationTool;
    private final SqlExecutionTool sqlExecutionTool;
    private final ReviewTool reviewTool;

    /**
     * 语义检索：向量召回 + 同义词扩展 + Rerank。
     *
     * @param question 用户自然语言问题
     * @param session  本次查询会话上下文（框架注入）
     * @return 候选元数据文本
     */
    @Tool(name = "retrieve_metadata",
            description = "语义检索：向量召回 + 同义词扩展 + Rerank，返回候选指标/维度/实体。"
                    + "识别意图后优先调用此工具。")
    public String retrieveMetadata(
            @ToolParam(name = "question", description = "用户的自然语言问题")
            String question,
            MultiAgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: retrieve_metadata ━━━");
        DslCandidate candidate = retrievalTool.retrieve(question);
        String contextText = candidateContextTool.buildCandidateContext(candidate);

        session.setCandidate(candidate);
        session.setCandidateContext(contextText);

        return "检索完成。候选元数据如下：\n" + contextText;
    }

    /**
     * 校验语义 DSL 合法性与兼容性。
     *
     * @param dsl     语义 DSL JSON
     * @param intent  意图类型
     * @param session 本次查询会话上下文（框架注入）
     * @return 校验结果文本
     */
    @Tool(name = "validate_dsl",
            description = "校验DSL的合法性与兼容性。传入DSL JSON和意图类型。")
    public String validateDsl(
            @ToolParam(name = "dsl",
                    description = "语义DSL的JSON字符串，格式: {metric,entity,dimensions,filters}")
            String dsl,
            @ToolParam(name = "intent",
                    description = "意图类型: METRIC_QUERY / DIMENSION_ANALYSIS / DETAIL_QUERY")
            String intent,
            MultiAgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: validate_dsl ━━━");
        try {
            SemanticQueryDSL semanticDsl = JSON.parseObject(dsl, SemanticQueryDSL.class);
            IntentResult.IntentType intentType = IntentResult.parseIntentType(intent);
            var result = validationTool.validate(semanticDsl, intentType);

            if (result.valid()) {
                session.setDsl(semanticDsl);
                session.setDslJson(dsl);
                return "DSL校验通过。DSL: " + dsl;
            }
            return "DSL校验失败，错误: " + result.errors()
                    + "。请修正DSL后重新调用 validate_dsl。";
        } catch (Exception e) {
            return "DSL解析失败: " + e.getMessage() + "。请确保DSL是合法JSON。";
        }
    }

    /**
     * DSL 富化：语义 code → 物理表/列，BFS 求解 JOIN。
     *
     * @param dsl      语义 DSL JSON
     * @param question 用户原始问题
     * @param session  本次查询会话上下文（框架注入）
     * @return 富化结果 JSON
     */
    @Tool(name = "enrich_dsl",
            description = "DSL富化：将语义code转为物理表/列，BFS求解JOIN路径。校验通过后调用。")
    public String enrichDsl(
            @ToolParam(name = "dsl", description = "语义DSL的JSON字符串")
            String dsl,
            @ToolParam(name = "question", description = "用户原始问题（用于补充检索候选集）")
            String question,
            MultiAgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: enrich_dsl ━━━");
        try {
            DslCandidate candidate = session.getCandidate();
            if (candidate == null) {
                candidate = retrievalTool.retrieve(question);
                session.setCandidate(candidate);
            }

            SemanticQueryDSL semanticDsl = JSON.parseObject(dsl, SemanticQueryDSL.class);
            EnrichedQueryDSL enriched = enrichmentTool.enrich(semanticDsl, candidate);
            String enrichedJson = JSON.toJSONString(enriched);

            session.setEnrichedDsl(enriched);
            session.setEnrichedDslJson(enrichedJson);

            return "DSL富化完成。富化后DSL: " + enrichedJson;
        } catch (Exception e) {
            return "DSL富化失败: " + e.getMessage();
        }
    }

    /**
     * 将富化后 DSL 翻译为参数化 SQL。
     *
     * @param enrichedDsl 富化后 DSL JSON
     * @param session     本次查询会话上下文（框架注入）
     * @return SQL 与参数
     */
    @Tool(name = "translate_sql",
            description = "将富化后的DSL翻译为参数化SQL。")
    public String translateSql(
            @ToolParam(name = "enriched_dsl",
                    description = "富化后DSL的JSON字符串（来自 enrich_dsl 的结果）")
            String enrichedDsl,
            MultiAgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: translate_sql ━━━");
        try {
            EnrichedQueryDSL enriched =
                    JSON.parseObject(enrichedDsl, EnrichedQueryDSL.class);
            var translated = translationTool.translate(enriched);
            String sql = translated.sql();
            List<Object> params = translated.parameters();

            session.setSql(sql);
            session.setParams(params);

            return "SQL翻译完成。\nSQL: " + sql + "\n参数: " + JSON.toJSONString(params);
        } catch (Exception e) {
            return "SQL翻译失败: " + e.getMessage();
        }
    }

    /**
     * LLM 审查 SQL 正确性。
     *
     * @param sql         待审查 SQL
     * @param enrichedDsl 富化后 DSL JSON
     * @return 审查结果文本
     */
    @Tool(name = "review_sql",
            description = "LLM审查SQL正确性。翻译后调用。")
    public String reviewSql(
            @ToolParam(name = "sql", description = "待审查的SQL语句")
            String sql,
            @ToolParam(name = "enriched_dsl",
                    description = "富化后DSL的JSON字符串（用于构建schema上下文）")
            String enrichedDsl) {
        log.info("━━━ [ReAct] 执行工具: review_sql ━━━");
        try {
            EnrichedQueryDSL enriched =
                    JSON.parseObject(enrichedDsl, EnrichedQueryDSL.class);
            String schema = translationTool.buildReviewSchema(enriched);
            ReviewResult review = reviewTool.reviewSql(sql, schema);

            if (Boolean.TRUE.equals(review.getResult())) {
                return "SQL审查通过。";
            }
            String reason = review.getReason() != null ? review.getReason() : "未知原因";
            return "SQL审查未通过: " + reason + "。请修正后重新调用 review_sql。";
        } catch (Exception e) {
            return "SQL审查异常: " + e.getMessage();
        }
    }

    /**
     * 安全执行 SQL（仅 SELECT）。
     *
     * @param sql     SQL 语句
     * @param params  SQL 参数列表
     * @param session 本次查询会话上下文（框架注入）
     * @return 查询结果文本
     */
    @Tool(name = "execute_sql",
            description = "安全执行SQL（仅SELECT）。审查通过后调用。返回查询结果。")
    public String executeSql(
            @ToolParam(name = "sql", description = "要执行的SQL语句")
            String sql,
            @ToolParam(name = "params", description = "SQL参数列表")
            List<Object> params,
            MultiAgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: execute_sql ━━━");
        List<Object> boundParams = new ArrayList<>();
        if (params != null) {
            boundParams.addAll(params);
        } else if (session.getParams() != null) {
            boundParams.addAll(session.getParams());
        }

        try {
            var result = sqlExecutionTool.execute(sql, boundParams);
            session.setQueryResult(result);
            return "SQL执行完成，返回 " + result.size() + " 行数据。\n查询结果: "
                    + JSON.toJSONString(result);
        } catch (Exception e) {
            return "SQL执行失败: " + e.getMessage();
        }
    }
}
