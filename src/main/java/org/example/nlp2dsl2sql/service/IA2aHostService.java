package org.example.nlp2dsl2sql.service;

import org.example.nlp2dsl2sql.a2a.trace.HostTraceRecord;
import org.example.nlp2dsl2sql.models.vo.A2aHostConfirmRequest;
import org.example.nlp2dsl2sql.models.vo.A2aHostConfirmResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * A2A Host 流式编排服务。
 */
public interface IA2aHostService {

    /**
     * 启动 Host Agent，流式返回 SSE 文本。
     *
     * @param sessionId HITL 会话 ID，可空（服务端生成）
     * @param question  用户问题
     * @return 文本流
     */
    Flux<String> chat(String sessionId, String question);

    /**
     * 确认或拒绝待执行 SQL。
     *
     * @param request 确认请求（以 rawInput 判定）
     * @return 确认结果
     */
    A2aHostConfirmResponse confirm(A2aHostConfirmRequest request);

    /**
     * 最近 trace 列表（按开始时间倒序）。
     *
     * @return trace 列表
     */
    List<HostTraceRecord> listTraces();

    /**
     * 单条 trace 详情。
     *
     * @param sessionId 会话 ID
     * @return trace；不存在返回 null
     */
    HostTraceRecord getTrace(String sessionId);
}
