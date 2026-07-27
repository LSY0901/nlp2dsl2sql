package org.example.nlp2dsl2sql.service;

import reactor.core.publisher.Flux;

/**
 * A2A Host 流式编排服务。
 */
public interface IA2aHostService {

    /**
     * 启动 Host Agent，流式返回 SSE 文本。
     *
     * @param question 用户问题
     * @return 文本流
     */
    Flux<String> chat(String question);
}
