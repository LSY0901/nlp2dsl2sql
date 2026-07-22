package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.models.request.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.service.INlp2dsl2sqlAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 多 Agent 协作 Controller。
 * <p>
 * 提供 SSE 流式接口，通过 Supervisor {@code HarnessAgent} + {@code @Tool} Toolkit
 * 完成自然语言到 SQL 的查询。
 * <p>
 * 与 {@link Nlp2Dsl2SqlAgentWorkFlowController}（V2 Workflow）共存，方便对比效果。
 *
 */
@RestController
@RequestMapping("/aiChat")
public class Nlp2Dsl2SqlAgentToolReActController {

    private final INlp2dsl2sqlAgentService multiAgentService;

    /**
     * 构造 Controller。
     *
     * @param multiAgentService 多 Agent 服务
     */
    public Nlp2Dsl2SqlAgentToolReActController(INlp2dsl2sqlAgentService multiAgentService) {
        this.multiAgentService = multiAgentService;
    }

    /**
     * 多 Agent 协作查询（SSE 真流式）。
     * <p>
     * 基于 {@code HarnessAgent#streamEvents}：工具调用进度与 LLM token 增量
     * 边生成边推送，而非整段结束后一次性返回。
     *
     * @param request 包含用户问题的请求
     * @return SSE 流式响应（文本增量）
     */

    /**
     * 这里不是多agent。是一个单agent + 多工具的ReAct
     * @param request
     * @return
     */
    @GetMapping(value = "/nlp2Dsl2SqlAgent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> multiAgentChat(Nlp2DslAgentRequest request) {
        return multiAgentService.nlp2dsl2sqlAgentQuery(request.getQuestion());
    }
}
