package org.example.nlp2dsl2sql.controller;


import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final HarnessAgent agent;

    public AgentController(HarnessAgent nlp2dslAgent){
        this.agent = nlp2dslAgent;
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
