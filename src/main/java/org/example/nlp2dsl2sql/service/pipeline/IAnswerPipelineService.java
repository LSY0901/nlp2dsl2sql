package org.example.nlp2dsl2sql.service.pipeline;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 自然语言回答 Pipeline Service（流式）。
 */
public interface IAnswerPipelineService {

    /**
     * 基于查询结果流式生成自然语言结论。
     *
     * @param question    用户问题
     * @param sql         执行 SQL
     * @param queryResult 查询结果
     * @return 文本增量流
     */
    Flux<String> streamAnswer(String question,
                              String sql,
                              List<Map<String, Object>> queryResult);
}
