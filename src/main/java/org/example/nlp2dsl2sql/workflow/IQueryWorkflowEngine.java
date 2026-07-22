package org.example.nlp2dsl2sql.workflow;

import reactor.core.publisher.Flux;

/**
 * 查询 Workflow 引擎：按 Planner 计划调度 Pipeline Service。
 */
public interface IQueryWorkflowEngine {

    /**
     * 执行 Planner-Workflow-Service 管线（SSE 流式输出）。
     *
     * @param question 用户自然语言问题
     * @return SSE 文本流
     */
    Flux<String> run(String question);
}
