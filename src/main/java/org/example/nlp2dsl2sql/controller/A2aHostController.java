package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.a2a.trace.HostTraceRecord;
import org.example.nlp2dsl2sql.models.vo.A2aHostConfirmRequest;
import org.example.nlp2dsl2sql.models.vo.A2aHostConfirmResponse;
import org.example.nlp2dsl2sql.models.vo.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.service.IA2aHostService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AgentScope A2A Host 入口。
 * <p>
 * 提供 SSE 流式接口、SQL HITL 确认接口与 trace 查询接口。
 */
@RestController
@RequestMapping("/aiChat")
public class A2aHostController {

    private final IA2aHostService a2aHostService;

    /**
     * 构造 Controller。
     *
     * @param a2aHostService Host 服务
     */
    public A2aHostController(IA2aHostService a2aHostService) {
        this.a2aHostService = a2aHostService;
    }

    /**
     * A2A Host 流式对话（含 SQL 执行前确认事件）。
     *
     * @param request 含 question / sessionId
     * @return SSE 流式响应
     */
    @GetMapping(value = "/a2aHost", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> a2aHost(Nlp2DslAgentRequest request) {
        return a2aHostService.chat(
                request.getSessionId(), request.getQuestion());
    }

    /**
     * 确认或拒绝待执行 SQL（以 rawInput 判定 yes/确认）。
     *
     * @param request 确认请求
     * @return 确认结果
     */
    @PostMapping(
            value = "/a2aHost/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public A2aHostConfirmResponse confirm(
            @RequestBody A2aHostConfirmRequest request) {
        return a2aHostService.confirm(request);
    }

    /**
     * 最近 trace 列表（按开始时间倒序）。
     *
     * @return trace 列表
     */
    @GetMapping(value = "/a2aHost/traces",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<HostTraceRecord> traces() {
        return a2aHostService.listTraces();
    }

    /**
     * 单条 trace 详情。
     *
     * @param sessionId 会话 ID
     * @return trace；不存在返回 null
     */
    @GetMapping(value = "/a2aHost/traces/{sessionId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public HostTraceRecord trace(@PathVariable String sessionId) {
        return a2aHostService.getTrace(sessionId);
    }
}
