package org.example.nlp2dsl2sql.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.prompt.SemanticPromptTemplates;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 语义 DSL 生成工具：基于问题、意图与候选元数据调用 LLM 生成 DSL。
 * <p>
 * 复用 {@link SemanticPromptTemplates#DSL_GENERATION_SYSTEM_PROMPT}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DslGenerationTool {

    private final OpenAIChatModel openAIChatModel;
    private final CandidateContextTool candidateContextTool;
    private final ObjectMapper objectMapper;

    /**
     * 生成语义查询 DSL。
     *
     * @param question  用户自然语言问题
     * @param intent    意图类型
     * @param candidate 检索候选集（用于构建候选上下文）
     * @return 语义 DSL
     */
    public SemanticQueryDSL generate(String question,
                                     IntentResult.IntentType intent,
                                     DslCandidate candidate) {
        String context = candidateContextTool.buildCandidateContext(candidate);
        return generateWithContext(question, intent, context);
    }

    /**
     * 使用已有候选上下文文本生成语义 DSL。
     *
     * @param question       用户自然语言问题
     * @param intent         意图类型
     * @param candidateText  候选元数据文本
     * @return 语义 DSL
     */
    public SemanticQueryDSL generateWithContext(String question,
                                                IntentResult.IntentType intent,
                                                String candidateText) {
        log.info("━━━ [Multi-Agent] DslGenerationTool 启动 ━━━");
        String userPrompt = buildUserPrompt(question, intent, candidateText);
        String response = callLlm(
                SemanticPromptTemplates.DSL_GENERATION_SYSTEM_PROMPT,
                userPrompt,
                GenerateOptions.builder().temperature(0.2).build());
        log.info("[Multi-Agent] DSL生成原始响应: {}", response);

        try {
            String json = extractJson(response);
            SemanticQueryDSL dsl =
                    objectMapper.readValue(json, SemanticQueryDSL.class);
            log.info("━━━ [Multi-Agent] DslGenerationTool 完成 ━━━");
            return dsl;
        } catch (Exception e) {
            log.error("DSL解析失败: {}, raw={}", e.getMessage(), response);
            throw new IllegalArgumentException(
                    "DSL解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 组装 DSL 生成用户提示词。
     *
     * @param question      用户问题
     * @param intent        意图类型
     * @param candidateText 候选元数据文本
     * @return 用户提示词
     */
    private String buildUserPrompt(String question,
                                   IntentResult.IntentType intent,
                                   String candidateText) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题: ").append(question).append("\n");
        sb.append("意图类型: ").append(intent.name()).append("\n");
        sb.append("\n候选元数据:\n");
        sb.append(candidateText != null ? candidateText : "");
        return sb.toString();
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
