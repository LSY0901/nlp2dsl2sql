package org.example.nlp2dsl2sql.service.pipeline.impl;

import com.alibaba.fastjson2.JSON;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.service.pipeline.IAnswerPipelineService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 自然语言回答 Pipeline Service（流式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerPipelineServiceImpl implements IAnswerPipelineService {

    private final OpenAIChatModel openAIChatModel;

    /**
     * 流式生成自然语言结论。
     *
     * @param question    用户问题
     * @param sql         执行 SQL
     * @param queryResult 查询结果
     * @return 文本增量流
     */
    @Override
    public Flux<String> streamAnswer(String question,
                                     String sql,
                                     List<Map<String, Object>> queryResult) {
        log.info("━━━ [Pipeline] ANSWER 开始流式输出 ━━━");
        String answerPrompt = """
                用户问题：%s

                查询SQL：%s

                查询结果：%s

                请用自然语言回答用户的问题，只输出结论本身，不要重复SQL或意图。
                """.formatted(question, sql, JSON.toJSONString(queryResult));

        var messages = List.of(
                Msg.builder().role(MsgRole.USER).textContent(answerPrompt).build()
        );
        return openAIChatModel.stream(messages, List.of(), null)
                .map(this::extractText)
                .filter(s -> s != null && !s.isEmpty());
    }

    /**
     * 从 ChatResponse 提取文本。
     *
     * @param resp 响应
     * @return 文本
     */
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
}
