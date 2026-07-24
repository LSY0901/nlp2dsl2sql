package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.models.vo.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.service.IAgentSkillWorkflowService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Agent-Skill-Workflow Controller（HarnessAgent + SkillToolGroup）。
 * <p>
 * Agent 加载 skill 后激活绑定工具组；会话身份在服务内写死。
 * 与 ReAct / V2 / PlannerWorkflow 接口并行共存。
 */
@RestController
@RequestMapping("/aiChat")
public class Nlp2Dsl2SqlAgentSkillWorkflowController {

    private final IAgentSkillWorkflowService agentSkillWorkflowService;

    /**
     * 构造 Controller。
     *
     * @param agentSkillWorkflowService Agent-Skill-Workflow 服务
     */
    public Nlp2Dsl2SqlAgentSkillWorkflowController(
            IAgentSkillWorkflowService agentSkillWorkflowService) {
        this.agentSkillWorkflowService = agentSkillWorkflowService;
    }

    /**
     * Agent-Skill-Workflow 查询（SSE）。
     *
     * @param request 包含用户问题的请求
     * @return SSE 流式响应
     */
    @GetMapping(value = "/nlp2Dsl2SqlAgentSkillWorkflow",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentSkillWorkflowChat(Nlp2DslAgentRequest request) {
        return agentSkillWorkflowService.run(request.getQuestion());
    }
}
