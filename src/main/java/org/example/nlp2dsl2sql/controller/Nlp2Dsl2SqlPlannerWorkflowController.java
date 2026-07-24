package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.models.vo.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.service.IQueryWorkflowEngine;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Planner-Workflow-Service 分层架构 Controller。
 * <p>
 * LLM（Planner）规划查询内容与步骤；Workflow 调度执行；
 * Pipeline Service 完成原子能力。与 ReAct / V2 接口并行存在。
 */
@RestController
@RequestMapping("/aiChat")
public class Nlp2Dsl2SqlPlannerWorkflowController {

    private final IQueryWorkflowEngine queryWorkflowEngine;

    /**
     * 构造 Controller。
     *
     * @param queryWorkflowEngine Workflow 引擎
     */
    public Nlp2Dsl2SqlPlannerWorkflowController(
            IQueryWorkflowEngine queryWorkflowEngine) {
        this.queryWorkflowEngine = queryWorkflowEngine;
    }

    /**
     * Planner-Workflow-Service 查询（SSE 流式）。
     *
     * @param request 包含用户问题的请求
     * @return SSE 文本流
     */
    @GetMapping(value = "/nlp2Dsl2SqlPlannerWorkflow",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> plannerWorkflowChat(Nlp2DslAgentRequest request) {
        return queryWorkflowEngine.run(request.getQuestion());
    }
}
