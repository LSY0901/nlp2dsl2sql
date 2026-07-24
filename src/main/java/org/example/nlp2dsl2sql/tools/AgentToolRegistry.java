package org.example.nlp2dsl2sql.tools;

import com.alibaba.fastjson2.JSON;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.dto.dsl.*;
import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Supervisor Agent 业务工具集。
 * <p>
 * 通过 {@link Tool} / {@link ToolParam} 注解注册到 AgentScope {@code Toolkit}，
 * 由 {@link io.agentscope.harness.agent.HarnessAgent} 在 ReAct 循环中自动调用。
 * <p>
 * 跨工具中间状态存放在 {@link AgentSessionContext}，由
 * {@link io.agentscope.core.agent.RuntimeContext} 按次注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolRegistry {

    private final IntentTool intentTool;
    private final RetrievalTool retrievalTool;
    private final CandidateContextTool candidateContextTool;
    private final DslGenerationTool dslGenerationTool;
    private final ValidationTool validationTool;
    private final EnrichmentTool enrichmentTool;
    private final TranslationTool translationTool;
    private final SqlExecutionTool sqlExecutionTool;
    private final ReviewTool reviewTool;

    /**
     * 意图识别：判断用户问题属于哪类查询意图。
     *
     * @param question 用户自然语言问题
     * @param session  本次查询会话上下文（框架注入）
     * @return 意图识别结果文本
     */
    @Tool(name = "classify_intent",
            description = """
                    作用：
                    识别用户自然语言问题的业务意图类型。
                    
                    适用场景：
                    - 收到用户问题后，首先判断查询类型。
                    - 需要区分指标查询、维度分析、明细查询与非业务问题。
                    
                    意图类型：
                    - METRIC_QUERY: 查询单个指标值。
                    - DIMENSION_ANALYSIS: 按维度对比/分析指标。
                    - DETAIL_QUERY: 查询明细数据。
                    - NON_BUSINESS: 非业务问题，无需走查询管线。
                    
                    输入：
                    用户原始问题。
                    
                    输出：
                    返回 intent、confidence、reason。
                    
                    限制：
                    只负责意图分类，不检索元数据，不生成DSL，不执行SQL。
                    """)
    public String classifyIntent(
            @ToolParam(name = "question", description = "用户原始自然语言问题")
            String question,
            AgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: classify_intent ━━━");
        IntentResult result = intentTool.classify(question);
        session.setIntentResult(result);
        session.setIntent(result.getIntent());
        return "意图识别完成。结果: " + JSON.toJSONString(result);
    }

    /**
     * 语义检索：向量召回 + 同义词扩展 + Rerank。
     *
     * @param question 用户自然语言问题
     * @param session  本次查询会话上下文（框架注入）
     * @return 候选元数据文本
     */
    @Tool(name = "retrieve_metadata",
            description = """
                    作用：
                    根据用户自然语言问题检索业务语义元数据，为DSL生成提供候选集。
                    
                    适用场景：
                    - 用户提出数据查询或分析需求。
                    - 需要识别指标、维度、实体、过滤条件等业务概念。
                    - 需要将业务术语映射到系统中的语义模型。
                    
                    能力：
                    - 向量语义检索。
                    - 同义词扩展。
                    - Rerank语义重排序。
                    
                    输入：
                    用户原始问题。
                    
                    输出：
                    返回候选指标、维度、实体、维度值、同义词提示及业务描述。
                    
                    限制：
                    仅负责语义元数据检索，不生成DSL，不生成SQL，不执行数据库查询。
                    """)
    public String retrieveMetadata(
            @ToolParam(name = "question", description = "用户原始自然语言问题（用于向量检索）")
            String question,
            AgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: retrieve_metadata ━━━");
        DslCandidate candidate = retrievalTool.retrieve(question);
        String contextText = candidateContextTool.buildCandidateContext(candidate);

        session.setCandidate(candidate);
        session.setCandidateContext(contextText);

        return "检索完成。候选元数据如下：\n" + contextText;
    }

    /**
     * 生成语义 DSL：基于问题、意图与候选元数据调用 LLM。
     *
     * @param question       用户问题
     * @param intent         意图类型
     * @param candidateText  候选元数据文本（可空，空则用 session）
     * @param session        本次查询会话上下文（框架注入）
     * @return 生成的 DSL JSON 文本
     */
    @Tool(name = "generate_dsl",
            description = """
                    作用：
                    根据用户问题、意图类型和候选元数据生成语义查询DSL。
                    
                    适用场景：
                    - 已完成意图识别与元数据检索，需要生成SemanticQueryDSL。
                    - validate_dsl失败后需要按错误信息重新生成DSL。
                    
                    处理能力：
                    - 从候选集中选择metric/entity/dimensions/filters。
                    - 按意图规则补全必填字段。
                    - 输出结构化SemanticQueryDSL JSON。
                    
                    输入：
                    用户问题、意图类型；候选元数据可显式传入，
                    或从会话上下文中的检索结果读取。
                    
                    输出：
                    返回SemanticQueryDSL JSON：
                    {metric,entity,dimensions,filters}。
                    
                    限制：
                    只负责生成语义DSL，不校验、不富化、不生成SQL。
                    禁止编造候选集中不存在的code。
                    """)
    public String generateDsl(
            @ToolParam(name = "question", description = "用户原始自然语言问题")
            String question,
            @ToolParam(name = "intent",
                    description = "意图类型: METRIC_QUERY / DIMENSION_ANALYSIS / "
                            + "DETAIL_QUERY（来自 classify_intent）")
            String intent,
            @ToolParam(name = "candidate_text", required = false,
                    description = "候选元数据文本（可空；为空时优先使用"
                            + "retrieve_metadata写入会话的上下文）")
            String candidateText,
            AgentSessionContext session) {
        log.info("━━━ [ReAct] 执行工具: generate_dsl ━━━");
        try {
            IntentResult.IntentType intentType =
                    IntentResult.parseIntentType(intent);
            if (session.getIntent() == null) {
                session.setIntent(intentType.name());
            }

            String context = resolveCandidateText(candidateText, question, session);
            SemanticQueryDSL dsl = dslGenerationTool.generateWithContext(
                    question, intentType, context);
            String dslJson = JSON.toJSONString(dsl);

            session.setDsl(dsl);
            session.setDslJson(dslJson);

            return "DSL生成完成。DSL: " + dslJson;
        } catch (Exception e) {
            return "DSL生成失败: " + e.getMessage()
                    + "。请检查候选元数据后重试 generate_dsl。";
        }
    }

    /**
     * 解析候选元数据文本：优先入参，其次 session，最后按问题重新检索。
     *
     * @param candidateText 显式传入的候选文本
     * @param question      用户问题
     * @param session       会话上下文
     * @return 候选元数据文本
     */
    private String resolveCandidateText(String candidateText,
                                        String question,
                                        AgentSessionContext session) {
        if (candidateText != null && !candidateText.isBlank()) {
            return candidateText;
        }
        if (session.getCandidateContext() != null
                && !session.getCandidateContext().isBlank()) {
            return session.getCandidateContext();
        }
        DslCandidate candidate = session.getCandidate();
        if (candidate == null) {
            candidate = retrievalTool.retrieve(question);
            session.setCandidate(candidate);
        }
        String context = candidateContextTool.buildCandidateContext(candidate);
        session.setCandidateContext(context);
        return context;
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
            description = """
                    作用：
                    验证语义DSL是否符合系统定义和业务查询规则。
                    
                    适用场景：
                    - 已通过generate_dsl生成SemanticQueryDSL，需要判断是否有效。
                    - 需要检查指标、维度、过滤条件是否合法。
                    
                    校验内容：
                    - 按意图检查必填字段（metric/entity/dimensions）。
                    - 指标是否存在。
                    - 维度是否有效。
                    - 实体与指标是否匹配。
                    - 过滤条件是否符合规则。
                    （指标与维度兼容性仅做告警，不作为硬失败。）
                    
                    输入：
                    语义DSL JSON以及查询意图类型。
                    
                    输出：
                    返回DSL校验结果以及错误原因。
                    
                    限制：
                    只负责DSL验证，不自动修改DSL，不生成SQL。
                    """)
    public String validateDsl(
            @ToolParam(name = "dsl",
                    description = "语义DSL的JSON字符串，格式: {metric,entity,dimensions,filters}")
            String dsl,
            @ToolParam(name = "intent",
                    description = "意图类型: METRIC_QUERY / DIMENSION_ANALYSIS / "
                            + "DETAIL_QUERY（来自 classify_intent）")
            String intent,
            AgentSessionContext session) {
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
                    + "。请调用 generate_dsl 重新生成，或修正后再次 validate_dsl。";
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
            description = """
                    作用：
                    将业务语义DSL转换为包含数据库执行信息的富化DSL。
                    
                    适用场景：
                    - 需要将业务概念映射到数据库结构。
                    - 需要补充查询执行所需的表、字段和关联关系。
                    
                    处理能力：
                    - 业务编码映射物理表和字段。
                    - 根据关系模型计算JOIN路径。
                    - 补充系统过滤条件。
                    
                    输入：
                    语义DSL JSON。
                    
                    输出：
                    返回富化后的查询DSL，包括表、字段、JOIN关系和查询约束。
                    
                    限制：
                    只负责DSL语义到数据库模型转换，不生成SQL，不执行查询。
                    """)
    public String enrichDsl(
            @ToolParam(name = "dsl", description = "语义DSL的JSON字符串（应与validate_dsl校验通过的DSL一致）")
            String dsl,
            @ToolParam(name = "question", description = "用户原始问题（用于补充检索候选集）")
            String question,
            AgentSessionContext session) {
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
            description = """
                    作用：
                    将富化后的DSL转换为数据库可执行的参数化SQL。
                    
                    适用场景：
                    - 需要将结构化查询模型转换为SQL语句。
                    
                    处理能力：
                    - DSL语义解析。
                    - 表字段转换。
                    - SQL生成。
                    - 查询参数绑定。
                    
                    输入：
                    富化后的EnrichedQueryDSL。
                    
                    输出：
                    返回SQL语句以及对应参数列表。
                    
                    限制：
                    只负责SQL生成，不执行SQL，不负责业务语义分析。
                    生成SQL必须使用参数绑定方式。
                    """)
    public String translateSql(
            @ToolParam(name = "enriched_dsl",
                    description = "富化后DSL的JSON字符串（来自 enrich_dsl 的结果）")
            String enrichedDsl,
            AgentSessionContext session) {
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
            description = """
                    作用：
                    检查SQL语句是否符合业务语义和数据库查询规范。
                    
                    适用场景：
                    - 需要判断SQL是否正确表达业务查询意图。
                    - 需要发现SQL生成过程中的潜在错误。
                    
                    检查内容：
                    - 查询字段是否正确。
                    - 指标计算逻辑是否合理。
                    - 聚合方式是否正确。
                    - JOIN关系是否符合业务模型。
                    - SQL是否存在明显风险。
                    
                    输入：
                    待检查SQL以及对应业务查询上下文。
                    
                    输出：
                    返回SQL审查结果以及问题说明。
                    
                    限制：
                    只负责SQL分析，不执行SQL，不自动修改SQL。
                    """)
    public String reviewSql(
            @ToolParam(name = "sql", description = "待审查的SQL语句（来自translate_sql的返回结果）")
            String sql,
            @ToolParam(name = "enriched_dsl",
                    description = "富化后DSL的JSON字符串（来自enrich_dsl的返回结果，用于构建schema上下文）")
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
            description = """
                    作用：
                    执行查询SQL并返回数据库查询结果。
                    
                    适用场景：
                    - 需要获取数据库中的业务数据结果。
                    
                    安全能力：
                    - SQL安全检查。
                    - 参数化查询。
                    - 限制危险数据库操作。
                    
                    输入：
                    SQL语句以及查询参数。
                    
                    输出：
                    返回查询结果数据。
                    
                    限制：
                    仅支持查询操作。
                    不负责SQL生成。
                    不负责SQL审查。
                    禁止执行数据修改类SQL。
                    """)
    public String executeSql(
            @ToolParam(name = "sql", description = "要执行的SQL语句（来自translate_sql的返回结果）")
            String sql,
            @ToolParam(name = "params", description = "SQL参数列表（可为null，为null时自动从上下文获取）")
            List<Object> params,
            AgentSessionContext session) {
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
