package org.example.nlp2dsl2sql.tools;

import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.formatter.ResponseFormat;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SQL 审查工具。
 * 改造：用 agentscope OpenAIChatModel 替代 Spring AI ChatClient。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewTool {

    private final OpenAIChatModel openAIChatModel;

    /**
     * 审查 SQL 是否正确。
     *
     * @param sql    待审查的 SQL
     * @param schema 上下文 schema 摘要
     * @return 审查结果
     */
    public ReviewResult reviewSql(String sql, String schema) {
        // DeepSeek 不支持 json_schema response_format，使用 jsonObject（提示词中已含格式说明）
        ResponseFormat responseFormat = ResponseFormat.jsonObject();

        String prompt = """
                你是 PostgreSQL 专家。请检查以下 SQL，并以 json 格式输出结果。

                检查项：
                1. 表是否存在
                2. 字段是否存在
                3. JOIN 是否合理
                4. GROUP BY 是否缺失
                5. 聚合函数是否正确
                6. WHERE 条件是否合理

                Schema:
                %s

                SQL:
                %s

                必须严格按以下 json 格式输出，不要加任何其他文字：
                {"result": true,"reason": "ok"}
                """.formatted(schema, sql);

        GenerateOptions options = GenerateOptions.builder()
                .responseFormat(responseFormat)
                .temperature(0.2)
                .build();

        String response = callLlm(prompt, options);

        try {
            String cleaned = cleanMarkdown(response);
            JSONObject parsed = JSON.parseObject(cleaned);
            ReviewResult result = new ReviewResult();
            result.setResult(parsed.getBoolean("result"));
            result.setReason(parsed.getString("reason"));
            return result;
        } catch (Exception e) {
            log.warn("reviewSql JSON 解析失败：{}，原始：{}", e.getMessage(), response);
            ReviewResult fallback = new ReviewResult();
            fallback.setResult(true);
            fallback.setReason("review 返回非 JSON，默认放行");
            return fallback;
        }
    }

    /**
     * 调用 LLM 并返回文本内容。
     */
    private String callLlm(String userPrompt, GenerateOptions options) {
        var messages = List.of(
                Msg.builder().role(MsgRole.SYSTEM).textContent("你是PostgreSQL SQL审查专家，只输出JSON。").build(),
                Msg.builder().role(MsgRole.USER).textContent(userPrompt).build()
        );
        // agentscope Model.stream 返回 Flux<ChatResponse>，取最后一个非空内容
        StringBuilder sb = new StringBuilder();
        openAIChatModel.stream(messages, List.of(), options)
                .doOnNext(resp -> {
                    if (resp.getContent() != null) {
                        for (var block : resp.getContent()) {
                            if (block instanceof io.agentscope.core.message.TextBlock tb) {
                                sb.append(tb.getText());
                            }
                        }
                    }
                })
                .blockLast();
        return sb.toString();
    }

    private String cleanMarkdown(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.trim()
                .replaceAll("^```(?:json)?\\s*\\n?", "")
                .replaceAll("\\n?```\\s*$", "")
                .trim();
    }
}
