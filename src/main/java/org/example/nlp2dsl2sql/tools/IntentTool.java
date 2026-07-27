package org.example.nlp2dsl2sql.tools;

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
import org.example.nlp2dsl2sql.intent.RuleIntentClassifier;
import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.example.nlp2dsl2sql.prompt.SemanticPromptTemplates;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 意图识别工具：优先规则匹配，未命中时调用 LLM 判断用户问题意图类型。
 * <p>
 * 复用 {@link SemanticPromptTemplates#INTENT_SYSTEM_PROMPT}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentTool {

    private final OpenAIChatModel openAIChatModel;
    private final ObjectMapper objectMapper;
    private final RuleIntentClassifier ruleIntentClassifier;

    /**
     * 识别用户问题的意图类型：先尝试规则匹配，未命中再调用 LLM。
     *
     * @param question 用户自然语言问题
     * @return 意图识别结果
     */
    public IntentResult classify(String question) {
        log.info("━━━ [Multi-Agent] IntentTool 启动 ━━━");
        Optional<IntentResult> ruled = ruleIntentClassifier.tryMatch(question);
        if (ruled.isPresent()) {
            IntentResult result = ruled.get();
            log.info("[Intent] RULE hit: {} reason={}",
                    result.getIntent(), result.getReason());
            return result;
        }
        log.info("[Intent] RULE miss → LLM");
        return classifyByLlm(question);
    }

    /**
     * 通过 LLM 识别用户问题的意图类型。
     *
     * @param question 用户自然语言问题
     * @return 意图识别结果
     */
    private IntentResult classifyByLlm(String question) {
        GenerateOptions options = GenerateOptions.builder()
                .responseFormat(ResponseFormat.jsonObject())
                .temperature(0.2)
                .build();

        String response = callLlm(
                SemanticPromptTemplates.INTENT_SYSTEM_PROMPT,
                question,
                options);

        try {
            String json = extractJson(response);
            IntentResult result = objectMapper.readValue(json, IntentResult.class);
            IntentResult.IntentType type = result.resolveIntentType();
            result.setIntent(type.name());
            log.info("━━━ [Multi-Agent] IntentTool 完成: intent={}, confidence={} ━━━",
                    result.getIntent(), result.getConfidence());
            return result;
        } catch (Exception e) {
            log.warn("意图识别解析失败，默认为 NON_BUSINESS: {}", e.getMessage());
            return buildFallback(e.getMessage());
        }
    }

    /**
     * 构建解析失败时的兜底意图结果。
     *
     * @param reason 失败原因
     * @return 兜底 IntentResult
     */
    private IntentResult buildFallback(String reason) {
        IntentResult fallback = new IntentResult();
        fallback.setIntent(IntentResult.IntentType.NON_BUSINESS.name());
        fallback.setConfidence(0.0);
        fallback.setReason("解析失败: " + reason);
        return fallback;
    }

    /**
     * 同步调用 LLM 并汇总文本内容。
     *
     * @param systemPrompt 系统提示
     * @param userPrompt   用户提示
     * @param options      生成选项
     * @return 完整响应文本
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
     * 将 ChatResponse 中的文本块追加到缓冲。
     *
     * @param resp 模型响应
     * @param sb   文本缓冲
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
     * 从模型输出中截取 JSON 对象子串。
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
