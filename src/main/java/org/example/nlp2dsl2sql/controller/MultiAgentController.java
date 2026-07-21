package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.models.request.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.semanticdsl.agent.IMultiAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 多 Agent 协作 Controller。
 * <p>
 * 提供 SSE 流式接口，通过 {@link IMultiAgentService} 编排多个 HarnessAgent + 工具函数
 * 完成自然语言到 SQL 的查询。
 * <p>
 * 与 {@link Nlp2Dsl2SqlAgentController}（V2 Workflow）共存，方便对比效果。
 *
 * @see org.example.nlp2dsl2sql.semanticdsl.agent.IMultiAgentService
 * @see org.example.nlp2dsl2sql.agent.MultiAgentConfig
 */
@RestController
@RequestMapping("/aiChat")
public class MultiAgentController {

    private final IMultiAgentService multiAgentService;

    public MultiAgentController(IMultiAgentService multiAgentService) {
        this.multiAgentService = multiAgentService;
    }

    /**
     * 多 Agent 协作查询（SSE 流式）。
     * <p>
     * 编排流程：意图识别 Agent → 检索工具 → DSL 生成 Agent → 校验工具
     * → 富化工具 → 翻译工具 → 审查 Agent → 执行工具 → 回答 Agent
     *
     * @param request 包含用户问题的请求
     * @return SSE 流式响应
     */
    @GetMapping(value = "/nlp2Dsl2SqlMultiAgent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> multiAgentChat(Nlp2DslAgentRequest request) {
        return multiAgentService.multiAgentQuery(request.getQuestion());
    }
}
