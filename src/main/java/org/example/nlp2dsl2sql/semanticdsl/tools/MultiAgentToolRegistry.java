package org.example.nlp2dsl2sql.semanticdsl.tools;

import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.example.nlp2dsl2sql.semanticdsl.model.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.model.IntentResult;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import org.example.nlp2dsl2sql.tools.ReviewTool;
import com.alibaba.fastjson2.JSON;
import io.agentscope.core.model.ToolSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多 Agent 工具注册表 — ReAct 循环的核心。
 * <p>
 * 定义所有可供 Supervisor Agent 调用的工具：
 * <ul>
 *   <li>{@link #buildToolSchemas()} — 构建 {@link ToolSchema} 列表，传给 {@code openAIChatModel.stream(messages, toolSchemas, options)}</li>
 *   <li>{@link #executeTool(String, Map)} — 根据 LLM 返回的 tool_call name 执行对应 Java 方法</li>
 * </ul>
 * <p>
 * LLM 通过 OpenAI Function Calling 看到工具 schema 后，自主决定调用哪个工具、传什么参数。
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

    /** 会话级上下文：在一次 ReAct 循环中跨工具传递中间结果 */
    private final ThreadLocal<Map<String, Object>> contextHolder = ThreadLocal.withInitial(HashMap::new);

    // ==================== 工具 Schema 构建 ====================

    /**
     * 构建 {@link ToolSchema} 列表，传给 {@code stream(messages, toolSchemas, options)} 的第二个参数。
     * <p>
     * LLM 收到这些 schema 后，通过 OpenAI Function Calling 自主决策调用哪个工具。
     *
     * @return 不可变的工具 schema 列表
     */
    public List<ToolSchema> buildToolSchemas() {
        return List.of(
                schema("retrieve_metadata",
                        "语义检索：向量召回 + 同义词扩展 + Rerank，返回候选指标/维度/实体。这是第一步，识别意图后调用。",
                        Map.of("question", strProp("用户的自然语言问题"))),

                schema("validate_dsl",
                        "校验DSL的合法性与兼容性。传入DSL JSON和意图类型。",
                        Map.of(
                                "dsl", strProp("语义DSL的JSON字符串，格式: {metric,entity,dimensions,filters}"),
                                "intent", strProp("意图类型: METRIC_QUERY / DIMENSION_ANALYSIS / DETAIL_QUERY")
                        )),

                schema("enrich_dsl",
                        "DSL富化：将语义code转为物理表/列，BFS求解JOIN路径。校验通过后调用。",
                        Map.of(
                                "dsl", strProp("语义DSL的JSON字符串"),
                                "question", strProp("用户原始问题（用于补充检索候选集）")
                        )),

                schema("translate_sql",
                        "将富化后的DSL翻译为参数化SQL。",
                        Map.of("enriched_dsl", strProp("富化后DSL的JSON字符串（来自 enrich_dsl 的结果）"))),

                schema("review_sql",
                        "LLM审查SQL正确性。翻译后调用。",
                        Map.of(
                                "sql", strProp("待审查的SQL语句"),
                                "enriched_dsl", strProp("富化后DSL的JSON字符串（用于构建schema上下文）")
                        )),

                schema("execute_sql",
                        "安全执行SQL（仅SELECT）。审查通过后调用。返回查询结果。",
                        Map.of(
                                "sql", strProp("要执行的SQL语句"),
                                "params", Map.of(
                                        "type", "array",
                                        "description", "SQL参数列表",
                                        "items", Map.of("type", "string")
                                )
                        )),

                schema("finish",
                        "所有步骤完成后调用此工具，传入最终的自然语言回答。调用后ReAct循环结束。",
                        Map.of("answer", strProp("给用户的最终自然语言回答")))
        );
    }

    // ==================== 工具执行分发器 ====================

    /**
     * 根据 LLM 返回的 tool_call name 执行对应工具，返回 tool_result 文本。
     *
     * @param toolName 工具名称（来自 {@link ToolUseBlock#getName()}）
     * @param input    工具参数（来自 {@link ToolUseBlock#getInput()}）
     * @return 工具执行结果文本
     */
    public String executeTool(String toolName, Map<String, Object> input) {
        log.info("━━━ [ReAct] 执行工具: {} ━━━", toolName);
        try {
            return switch (toolName) {
                case "retrieve_metadata" -> toolRetrieveMetadata(input);
                case "validate_dsl" -> toolValidateDsl(input);
                case "enrich_dsl" -> toolEnrichDsl(input);
                case "translate_sql" -> toolTranslateSql(input);
                case "review_sql" -> toolReviewSql(input);
                case "execute_sql" -> toolExecuteSql(input);
                case "finish" -> (String) input.getOrDefault("answer", "");
                default -> "错误: 未知工具 " + toolName;
            };
        } catch (Exception e) {
            log.error("[ReAct] 工具执行异常: {} - {}", toolName, e.getMessage(), e);
            return "错误: 工具 " + toolName + " 执行失败 - " + e.getMessage();
        }
    }

    // ==================== 各工具实现 ====================

    private String toolRetrieveMetadata(Map<String, Object> input) {
        String question = (String) input.get("question");
        DslCandidate candidate = retrievalTool.retrieve(question);
        String contextText = candidateContextTool.buildCandidateContext(candidate);

        getContext().put("candidate", candidate);
        getContext().put("candidateContext", contextText);

        return "检索完成。候选元数据如下：\n" + contextText;
    }

    private String toolValidateDsl(Map<String, Object> input) {
        String dslJson = (String) input.get("dsl");
        String intent = (String) input.get("intent");

        try {
            SemanticQueryDSL dsl = JSON.parseObject(dslJson, SemanticQueryDSL.class);
            IntentResult.IntentType intentType = IntentResult.parseIntentType(intent);
            var result = validationTool.validate(dsl, intentType);

            if (result.valid()) {
                getContext().put("dsl", dsl);
                getContext().put("dslJson", dslJson);
                return "DSL校验通过。DSL: " + dslJson;
            } else {
                return "DSL校验失败，错误: " + result.errors() + "。请修正DSL后重新调用 validate_dsl。";
            }
        } catch (Exception e) {
            return "DSL解析失败: " + e.getMessage() + "。请确保DSL是合法JSON。";
        }
    }

    private String toolEnrichDsl(Map<String, Object> input) {
        String dslJson = (String) input.get("dsl");
        String question = (String) input.get("question");

        DslCandidate candidate = (DslCandidate) getContext().get("candidate");
        if (candidate == null) {
            candidate = retrievalTool.retrieve(question);
            getContext().put("candidate", candidate);
        }

        try {
            SemanticQueryDSL dsl = JSON.parseObject(dslJson, SemanticQueryDSL.class);
            EnrichedQueryDSL enriched = enrichmentTool.enrich(dsl, candidate);
            String enrichedJson = JSON.toJSONString(enriched);

            getContext().put("enrichedDsl", enriched);
            getContext().put("enrichedDslJson", enrichedJson);

            return "DSL富化完成。富化后DSL: " + enrichedJson;
        } catch (Exception e) {
            return "DSL富化失败: " + e.getMessage();
        }
    }

    private String toolTranslateSql(Map<String, Object> input) {
        String enrichedDslJson = (String) input.get("enriched_dsl");

        try {
            EnrichedQueryDSL enriched = JSON.parseObject(enrichedDslJson, EnrichedQueryDSL.class);
            var translated = translationTool.translate(enriched);
            String sql = translated.sql();
            List<Object> params = translated.parameters();

            getContext().put("sql", sql);
            getContext().put("params", params);

            return "SQL翻译完成。\nSQL: " + sql + "\n参数: " + JSON.toJSONString(params);
        } catch (Exception e) {
            return "SQL翻译失败: " + e.getMessage();
        }
    }

    private String toolReviewSql(Map<String, Object> input) {
        String sql = (String) input.get("sql");
        String enrichedDslJson = (String) input.get("enriched_dsl");

        try {
            EnrichedQueryDSL enriched = JSON.parseObject(enrichedDslJson, EnrichedQueryDSL.class);
            String schema = translationTool.buildReviewSchema(enriched);
            ReviewResult review = reviewTool.reviewSql(sql, schema);

            if (Boolean.TRUE.equals(review.getResult())) {
                return "SQL审查通过。";
            } else {
                String reason = review.getReason() != null ? review.getReason() : "未知原因";
                return "SQL审查未通过: " + reason + "。请修正后重新调用 review_sql。";
            }
        } catch (Exception e) {
            return "SQL审查异常: " + e.getMessage();
        }
    }

    private String toolExecuteSql(Map<String, Object> input) {
        String sql = (String) input.get("sql");
        List<Object> params = new ArrayList<>();
        Object paramsRaw = input.get("params");
        if (paramsRaw instanceof List<?> list) {
            params.addAll(list);
        }

        try {
            var result = sqlExecutionTool.execute(sql, params);
            getContext().put("queryResult", result);

            return "SQL执行完成，返回 " + result.size() + " 行数据。\n查询结果: " + JSON.toJSONString(result);
        } catch (Exception e) {
            return "SQL执行失败: " + e.getMessage();
        }
    }

    // ==================== 会话上下文管理 ====================

    public Map<String, Object> getContext() {
        return contextHolder.get();
    }

    public void clearContext() {
        contextHolder.remove();
    }

    // ==================== ToolSchema 辅助方法 ====================

    private ToolSchema schema(String name, String description, Map<String, Object> properties) {
        return ToolSchema.builder()
                .name(name)
                .description(description)
                .parameters(Map.of(
                        "type", "object",
                        "properties", properties
                ))
                .build();
    }

    private Map<String, Object> strProp(String description) {
        return Map.of("type", "string", "description", description);
    }
}
