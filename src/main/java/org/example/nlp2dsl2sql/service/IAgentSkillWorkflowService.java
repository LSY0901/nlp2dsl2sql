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

    /**
     * 执行 Agent-Skill-Workflow 查询（带会话与租户身份）。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param question  用户自然语言问题
     * @return SSE 文本流
     */
    default Flux<String> run(String sessionId, String userId, String question) {
        return run(question);
    }
}
