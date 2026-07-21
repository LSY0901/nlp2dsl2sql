package org.example.nlp2dsl2sql.controller;


import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final HarnessAgent agent;

    /**
     * 注入基础对话 Agent（与 supervisorAgent 区分）。
     *
     * @param nlp2dsl2sqlAgent 基础 HarnessAgent
     */
    public AgentController(@Qualifier("nlp2dsl2sqlAgent") HarnessAgent nlp2dsl2sqlAgent) {
        this.agent = nlp2dsl2sqlAgent;
    }

    @GetMapping("/chat")
    public String chat(String question) {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("supervisor")
                .userId("lsy")
                .build();
        return agent.call(new UserMessage(question), ctx).toString();
    }

}
