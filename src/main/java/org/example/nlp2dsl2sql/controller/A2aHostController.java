package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.models.vo.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.service.IA2aHostService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AgentScope A2A Host 入口。
 * <p>
 * 提供 SSE 流式接口，Host Agent 通过 A2A 协议调度远程 Agent 完成用户请求。
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
     * A2A Host 流式对话。
     * <p>
     * 基于 {@code HarnessAgent#streamEvents}：A2A 远程工具调用进度与 LLM token
     * 增量边生成边推送。
     *
     * @param request 包含用户问题的请求
     * @return SSE 流式响应（文本增量）
     */
    @GetMapping(value = "/a2aHost", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> a2aHost(Nlp2DslAgentRequest request) {
        return a2aHostService.chat(request.getQuestion());
    }
}
