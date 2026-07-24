package org.example.nlp2dsl2sql.planner.impl;

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
import org.example.nlp2dsl2sql.planner.IQueryPlanner;
import org.example.nlp2dsl2sql.enums.planner.FailureAction;
import org.example.nlp2dsl2sql.models.entity.planner.PlanStep;
import org.example.nlp2dsl2sql.models.entity.planner.QueryPlan;
import org.example.nlp2dsl2sql.enums.planner.StepType;
import org.example.nlp2dsl2sql.prompt.PlannerPromptTemplates;
import org.example.nlp2dsl2sql.exception.Nlp2dsl2sqlException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 查询规划器实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryPlanner implements IQueryPlanner {

    private final OpenAIChatModel openAIChatModel;
    private final ObjectMapper objectMapper;

    /**
     * 生成初始查询计划。
     *
     * @param question 用户问题
     * @return 查询计划
     */
    @Override
    public QueryPlan plan(String question) {
        log.info("━━━ [Planner] 初次规划 ━━━");
        GenerateOptions options = GenerateOptions.builder()
                .responseFormat(ResponseFormat.jsonObject())
                .temperature(0.2)
                .build();
        String response = callLlm(
                PlannerPromptTemplates.PLAN_SYSTEM_PROMPT,
                "用户问题: " + question,
                options);
        return parseAndNormalize(response);
    }

    /**
     * 失败后重规划。
     *
     * @param question       用户问题
     * @param previousPlan   上一份计划
     * @param failedStep     失败步骤
     * @param errorMessage   错误信息
     * @param contextSummary 上下文摘要
     * @return 新计划
     */
    @Override
    public QueryPlan replan(String question,
                            QueryPlan previousPlan,
                            StepType failedStep,
                            String errorMessage,
                            String contextSummary) {
        log.info("━━━ [Planner] 重规划: failedStep={} ━━━", failedStep);
        String userPrompt = """
                用户问题: %s
                失败步骤: %s
                错误信息: %s
                上一份计划: %s
                上下文摘要: %s
                请输出修正后的 QueryPlan JSON。
                """.formatted(
                question,
                failedStep == null ? "UNKNOWN" : failedStep.name(),
                errorMessage,
                JSON.toJSONString(previousPlan),
                contextSummary == null ? "" : contextSummary);

        GenerateOptions options = GenerateOptions.builder()
                .responseFormat(ResponseFormat.jsonObject())
                .temperature(0.2)
                .build();
        String response = callLlm(
                PlannerPromptTemplates.REPLAN_SYSTEM_PROMPT,
                userPrompt,
                options);
        return parseAndNormalize(response);
    }

    /**
     * 解析并规范化计划（补默认值）。
     *
     * @param response LLM 原始响应
     * @return QueryPlan
     */
    private QueryPlan parseAndNormalize(String response) {
        try {
            String json = extractJson(response);
            QueryPlan plan = objectMapper.readValue(json, QueryPlan.class);
            if (plan.getMaxReplan() <= 0) {
                plan.setMaxReplan(2);
            }
            if (plan.getSteps() != null) {
                for (PlanStep step : plan.getSteps()) {
                    normalizeStep(step);
                }
            }
            log.info("[Planner] 计划: intent={}, steps={}",
                    plan.getIntent(),
                    plan.getSteps() == null ? 0 : plan.getSteps().size());
            return plan;
        } catch (Exception e) {
            log.error("计划解析失败: {}, raw={}", e.getMessage(), response);
            throw new Nlp2dsl2sqlException("查询计划解析失败，请重试", e);
        }
    }

    /**
     * 规范化单步字段。
     *
     * @param step 计划步骤
     */
    private void normalizeStep(PlanStep step) {
        if (step.getOnFailure() == null) {
            step.setOnFailure(FailureAction.ABORT);
        }
        if (step.getRetry() < 0) {
            step.setRetry(0);
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
     * 追加响应文本到缓冲。
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
     * 从文本截取 JSON。
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
