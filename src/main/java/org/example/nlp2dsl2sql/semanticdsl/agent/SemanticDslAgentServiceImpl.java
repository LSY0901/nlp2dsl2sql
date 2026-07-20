package org.example.nlp2dsl2sql.semanticdsl.agent;

import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import org.example.nlp2dsl2sql.semanticdsl.enricher.SemanticDslEnricher;
import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.example.nlp2dsl2sql.semanticdsl.model.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.model.IntentResult;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.prompt.SemanticPromptTemplates;
import org.example.nlp2dsl2sql.semanticdsl.retriever.DslRetriever;
import org.example.nlp2dsl2sql.semanticdsl.translator.DslTranslator;
import org.example.nlp2dsl2sql.semanticdsl.validator.SemanticDslValidator;
import org.example.nlp2dsl2sql.tools.ReviewTool;
import org.example.nlp2dsl2sql.tools.SqlExecuteTool;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.formatter.ResponseFormat;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * NLP2DSL2SQL Agent V2 — 语义层管线实现。
 * 改造：用 agentscope OpenAIChatModel 替代 Spring AI ChatClient/ChatModel。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticDslAgentServiceImpl implements ISemanticDslAgentService {

    private final OpenAIChatModel openAIChatModel;
    private final DslRetriever dslRetriever;
    private final SemanticDslEnricher dslEnricher;
    private final SemanticDslValidator dslValidator;
    private final DslTranslator dslTranslator;
    private final ReviewTool reviewTool;
    private final SqlExecuteTool sqlExecuteTool;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<String> nlp2Dsl2SqlAgentV2(String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }

        return Flux.defer(() -> {
            try {
                return runPipeline(question.trim());
            } catch (PipelineException e) {
                log.warn("Agent业务失败: {}", e.getMessage());
                return Flux.just("错误: " + e.getMessage());
            } catch (Exception e) {
                log.error("Agent执行失败", e);
                return Flux.just("错误: 系统处理失败，请稍后重试");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<String> runPipeline(String question) throws Exception {
        log.info("━━━━━━━ NLP2DSL2SQL Agent V2 启动 ━━━━━━━");
        log.info("用户问题: {}", question);

        // Stage 1: 意图识别
        IntentResult intent = classifyIntent(question);
        IntentResult.IntentType intentType = intent.resolveIntentType();
        log.info("[Stage 1] 意图识别: {} (confidence={})", intentType, intent.getConfidence());

        if (intentType == IntentResult.IntentType.NON_BUSINESS) {
            throw new PipelineException("非业务问题，无法处理。原因: " + intent.getReason());
        }

        // Stage 2: 语义检索
        DslCandidate candidate = dslRetriever.retrieve(question);
        log.info("[Stage 2] 语义检索完成: metrics={}, dimensions={}",
                sizeOf(candidate.getMetrics()), sizeOf(candidate.getDimensions()));
        if (sizeOf(candidate.getMetrics()) == 0 && sizeOf(candidate.getEntities()) == 0) {
            throw new PipelineException("未检索到相关指标/实体，请换一种问法");
        }

        // Stage 3: 语义DSL生成
        SemanticQueryDSL semanticDSL = generateSemanticDSL(question, candidate, intentType);
        log.info("[Stage 3] 语义DSL: {}", objectMapper.writeValueAsString(semanticDSL));

        // Stage 4: DSL校验
        SemanticDslValidator.ValidationResult validation =
                dslValidator.validate(semanticDSL, intentType);
        log.info("[Stage 4] 校验结果: valid={}", validation.valid());
        if (!validation.valid()) {
            throw new PipelineException("DSL校验失败: " + validation.errors());
        }

        // Stage 5: DSL富化
        EnrichedQueryDSL enrichedDSL = dslEnricher.enrich(semanticDSL, candidate);
        log.info("[Stage 5] DSL富化完成");
        if (enrichedDSL.getMainPhysicalTable() == null
                || enrichedDSL.getSelectColumns() == null
                || enrichedDSL.getSelectColumns().isEmpty()) {
            throw new PipelineException("DSL富化结果不完整，无法生成SQL");
        }

        // Stage 6: SQL生成
        DslTranslator.TranslatedSql translated = dslTranslator.translate(enrichedDSL);
        String sql = translated.sql();
        List<Object> params = translated.parameters();
        log.info("[Stage 6] SQL生成: {}, params={}", sql, params);

        // Stage 7: SQL审查
        enforceReviewGate(sql, buildReviewSchema(enrichedDSL), question);
        log.info("[Stage 7] SQL审查完成");

        // 执行SQL
        List<Map<String, Object>> queryResult = sqlExecuteTool.executeSql(sql, params);
        log.info("━━━━━━━ NLP2DSL2SQL Agent V2 管线完成，开始流式回答 ━━━━━━━");

        String answerPrompt = """
                用户问题：%s

                查询SQL：%s

                查询结果：%s

                请用自然语言回答用户的问题，只输出结论本身，不要重复SQL或意图。
                """.formatted(question, sql, JSON.toJSONString(queryResult));

        String intentBlock = "意图：" + intentType.name() + "\n\n";
        String sqlBlock = "SQL：\n" + sql + "\n\n结论：\n";

        return Flux.concat(
                Flux.just(intentBlock),
                Flux.just(sqlBlock),
                streamLlmAnswer(answerPrompt)
        );
    }

    @Override
    public IntentResult classifyIntent(String question) {
        // DeepSeek 不支持 json_schema response_format，使用 jsonObject（提示词中已含格式说明）
        GenerateOptions options = GenerateOptions.builder()
                .responseFormat(ResponseFormat.jsonObject())
                .temperature(0.2)
                .build();

        String response = callLlm(
                SemanticPromptTemplates.INTENT_SYSTEM_PROMPT,
                question,
                options
        );

        try {
            String json = extractJson(response);
            IntentResult result = objectMapper.readValue(json, IntentResult.class);
            IntentResult.IntentType type = result.resolveIntentType();
            result.setIntent(type.name());
            return result;
        } catch (Exception e) {
            log.warn("意图识别解析失败，默认为NON_BUSINESS: {}", e.getMessage());
            IntentResult fallback = new IntentResult();
            fallback.setIntent(IntentResult.IntentType.NON_BUSINESS.name());
            fallback.setConfidence(0.0);
            fallback.setReason("解析失败: " + e.getMessage());
            return fallback;
        }
    }


    private SemanticQueryDSL generateSemanticDSL(String question,
                                                 DslCandidate candidate,
                                                 IntentResult.IntentType intentType) {
        String context = buildCandidateContext(candidate);
        String userPrompt = "用户问题: " + question
                + "\n意图类型: " + intentType.name()
                + "\n\n候选元数据:\n" + context;

        String response = callLlm(
                SemanticPromptTemplates.DSL_GENERATION_SYSTEM_PROMPT,
                userPrompt,
                GenerateOptions.builder().temperature(0.2).build()
        );
        log.info("[Stage 3] DSL生成原始响应: {}", response);

        try {
            String json = extractJson(response);
            return objectMapper.readValue(json, SemanticQueryDSL.class);
        } catch (Exception e) {
            log.error("DSL解析失败: {}, raw={}", e.getMessage(), response);
            throw new PipelineException("DSL解析失败，请重试");
        }
    }

    private String buildCandidateContext(DslCandidate candidate) {
        StringBuilder context = new StringBuilder();
        context.append("可用指标:\n");
        if (candidate.getMetrics() != null) {
            candidate.getMetrics().forEach(m -> context.append("- ").append(m.getMetricCode())
                    .append("(").append(m.getMetricName()).append("): ")
                    .append(m.getDescription()).append("\n"));
        }
        context.append("\n可用维度:\n");
        if (candidate.getDimensions() != null) {
            candidate.getDimensions().forEach(d -> context.append("- ").append(d.getDimensionCode())
                    .append("(").append(d.getDimensionName()).append("): ")
                    .append(d.getDescription()).append("\n"));
        }
        context.append("\n可用实体:\n");
        if (candidate.getEntities() != null) {
            candidate.getEntities().forEach(e -> context.append("- ").append(e.getEntityCode())
                    .append("(").append(e.getEntityName()).append(")\n"));
        }
        context.append("\n维度值:\n");
        if (candidate.getDimensionValues() != null) {
            candidate.getDimensionValues().forEach(v -> context.append("- ")
                    .append(v.getDimensionCode()).append(".")
                    .append(v.getValueCode()).append(" = ")
                    .append(v.getValueName()).append("\n"));
        }
        context.append("\n同义词提示:\n");
        if (candidate.getSynonyms() != null) {
            candidate.getSynonyms().forEach(s -> context.append("- ")
                    .append(s.getSynonymText())
                    .append(" → ").append(s.getObjectType())
                    .append(":").append(s.getObjectCode())
                    .append("(").append(s.getStandardName()).append(")\n"));
        }
        return context.toString();
    }

    private void enforceReviewGate(String sql, String schema, String question) {
        String reviewContext = schema + "\n用户问题: " + question;
        try {
            ReviewResult review = reviewTool.reviewSql(sql, reviewContext);
            boolean passed = Boolean.TRUE.equals(review.getResult());
            if (passed) {
                log.info("SQL审查通过");
                return;
            }
            String reason = review.getReason() != null ? review.getReason() : "未知原因";
            log.warn("SQL审查未通过: {}", reason);
            throw new PipelineException("SQL审查未通过: " + reason);
        } catch (PipelineException e) {
            throw e;
        } catch (Exception e) {
            log.error("SQL审查异常", e);
            throw new PipelineException("SQL审查服务异常，已中止执行");
        }
    }

    private String buildReviewSchema(EnrichedQueryDSL dsl) {
        StringBuilder sb = new StringBuilder();
        sb.append("主表: ").append(dsl.getMainPhysicalTable()).append("\n");
        if (dsl.getSelectColumns() != null) {
            sb.append("SELECT列:\n");
            dsl.getSelectColumns().forEach(c ->
                    sb.append("- ").append(c.getExpression())
                            .append(" AS ").append(c.getAlias()).append("\n"));
        }
        if (dsl.getJoins() != null) {
            sb.append("JOIN:\n");
            dsl.getJoins().forEach(j ->
                    sb.append("- ").append(j.getJoinType()).append(" ")
                            .append(j.getPhysicalTable()).append(" ON ")
                            .append(j.getOnCondition()).append("\n"));
        }
        if (dsl.getWhereConditions() != null) {
            sb.append("WHERE:\n");
            dsl.getWhereConditions().forEach(w ->
                    sb.append("- ").append(w.getExpression()).append("\n"));
        }
        return sb.toString();
    }

    // ====== agentscope LLM 调用封装 ======

    /**
     * 同步调用 LLM（system + user），返回完整文本。
     * agentscope Model.stream 返回 Flux<ChatResponse>，聚合后返回。
     */
    private String callLlm(String systemPrompt, String userPrompt, GenerateOptions options) {
        var messages = List.of(
                Msg.builder().role(MsgRole.SYSTEM).textContent(systemPrompt).build(),
                Msg.builder().role(MsgRole.USER).textContent(userPrompt).build()
        );
        StringBuilder sb = new StringBuilder();
        openAIChatModel.stream(messages, List.of(), options)
                .doOnNext(resp -> appendContent(resp, sb))
                .blockLast();
        return sb.toString();
    }

    /**
     * 流式调用 LLM，返回逐 token 的 Flux<String>。
     */
    private Flux<String> streamLlmAnswer(String userPrompt) {
        var messages = List.of(
                Msg.builder().role(MsgRole.USER).textContent(userPrompt).build()
        );
        return openAIChatModel.stream(messages, List.of(), null)
                .map(this::extractText)
                .filter(s -> s != null && !s.isEmpty());
    }

    private void appendContent(ChatResponse resp, StringBuilder sb) {
        if (resp.getContent() != null) {
            for (var block : resp.getContent()) {
                if (block instanceof TextBlock tb) {
                    sb.append(tb.getText());
                }
            }
        }
    }

    private String extractText(ChatResponse resp) {
        if (resp.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var block : resp.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    // ====== 工具方法 ======

    private String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static class PipelineException extends RuntimeException {
        PipelineException(String message) {
            super(message);
        }
    }
}
