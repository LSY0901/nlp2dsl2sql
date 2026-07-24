package org.example.nlp2dsl2sql.service.pipeline.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.entity.planner.PlanGoal;
import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.prompt.SemanticPromptTemplates;
import org.example.nlp2dsl2sql.tools.CandidateContextTool;
import org.example.nlp2dsl2sql.service.pipeline.IDslGeneratePipelineService;
import org.example.nlp2dsl2sql.exception.Nlp2dsl2sqlException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 语义 DSL 生成 Pipeline Service（独立 LLM 调用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DslGeneratePipelineServiceImpl implements IDslGeneratePipelineService {

    private final OpenAIChatModel openAIChatModel;
    private final CandidateContextTool candidateContextTool;
    private final ObjectMapper objectMapper;

    /**
     * 生成语义 DSL。
     *
     * @param question  用户问题
     * @param candidate 候选元数据
     * @param intent    意图类型
     * @param goal      规划目标提示
     * @return 语义 DSL
     */
    @Override
    public SemanticQueryDSL generate(String question,
                                     DslCandidate candidate,
                                     IntentResult.IntentType intent,
                                     PlanGoal goal) {
        log.info("━━━ [Pipeline] GENERATE_DSL 开始 ━━━");
        String context = candidateContextTool.buildCandidateContext(candidate);
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("用户问题: ").append(question).append("\n");
        userPrompt.append("意图类型: ").append(intent.name()).append("\n");
        appendGoalHints(userPrompt, goal);
        userPrompt.append("\n候选元数据:\n").append(context);

        String response = callLlm(
                SemanticPromptTemplates.DSL_GENERATION_SYSTEM_PROMPT,
                userPrompt.toString(),
                GenerateOptions.builder().temperature(0.2).build());
        log.info("[Pipeline] DSL生成原始响应: {}", response);

        try {
            String json = extractJson(response);
            SemanticQueryDSL dsl =
                    objectMapper.readValue(json, SemanticQueryDSL.class);
            log.info("━━━ [Pipeline] GENERATE_DSL 完成 ━━━");
            return dsl;
        } catch (Exception e) {
            log.error("DSL解析失败: {}, raw={}", e.getMessage(), response);
            throw new Nlp2dsl2sqlException("DSL解析失败，请重试", e);
        }
    }

    /**
     * 将规划目标提示追加到用户提示词。
     *
     * @param sb   提示词缓冲
     * @param goal 规划目标
     */
    private void appendGoalHints(StringBuilder sb, PlanGoal goal) {
        if (goal == null) {
            return;
        }
        sb.append("规划目标提示:\n");
        if (goal.getMetricHint() != null) {
            sb.append("- metricHint: ").append(goal.getMetricHint()).append("\n");
        }
        if (goal.getDimensionHints() != null && !goal.getDimensionHints().isEmpty()) {
            sb.append("- dimensionHints: ")
                    .append(goal.getDimensionHints()).append("\n");
        }
        if (goal.getFilterHints() != null && !goal.getFilterHints().isEmpty()) {
            sb.append("- filterHints: ").append(goal.getFilterHints()).append("\n");
        }
    }

    /**
     * 同步调用 LLM。
     *
     * @param systemPrompt 系统提示
     * @param userPrompt   用户提示
     * @param options      生成选项
     * @return 完整文本
     */
    private String callLlm(String systemPrompt,
                           String userPrompt,
                           GenerateOptions options) {
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
     * 追加 ChatResponse 文本到缓冲。
     *
     * @param resp 响应
     * @param sb   缓冲
     */
    private void appendContent(ChatResponse resp, StringBuilder sb) {
        if (resp.getContent() == null) {
            return;
        }
        for (var block : resp.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
    }

    /**
     * 从文本中截取 JSON 对象。
     *
     * @param text 原始文本
     * @return JSON 子串
     */
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
}
