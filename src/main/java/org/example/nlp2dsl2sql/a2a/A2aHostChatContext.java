package org.example.nlp2dsl2sql.a2a;

import lombok.Getter;
import reactor.core.publisher.Sinks;

/**
 * 单次 A2A Host SSE 请求上下文（经 RuntimeContext 注入工具）。
 */
@Getter
public class A2aHostChatContext {

    private final String sessionId;
    private final Sinks.Many<String> sseSink;

    /**
     * 构造 Host 聊天上下文。
     *
     * @param sessionId 会话 ID
     */
    public A2aHostChatContext(String sessionId) {
        this.sessionId = sessionId;
        this.sseSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    /**
     * 向 Host SSE 桥推送文本（失败静默，不抛异常）。
     *
     * @param chunk 文本增量
     */
    public void emit(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        sseSink.tryEmitNext(chunk);
    }

    /**
     * SSE 结束时完成 Sink。
     */
    public void complete() {
        sseSink.tryEmitComplete();
    }
}
