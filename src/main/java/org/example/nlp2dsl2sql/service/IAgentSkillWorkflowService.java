package org.example.nlp2dsl2sql.service;

import reactor.core.publisher.Flux;

/**
 * Agent-Skill-Workflow 查询服务（HarnessAgent + SkillToolGroup）。
 * <p>
 * Agent 加载 Skill 后激活对应工具组；会话身份在服务内写死。
 */
public interface IAgentSkillWorkflowService {

    /**
     * 执行 Agent-Skill-Workflow 查询（SSE）。
     *
     * @param question 用户自然语言问题
     * @return SSE 文本流
     */
    Flux<String> run(String question);
}
