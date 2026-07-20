package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.models.request.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.semanticdsl.agent.ISemanticDslAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/aiChat")
public class Nlp2Dsl2SqlAgentController {

    @Autowired
    private ISemanticDslAgentService semanticDslAgentService;

    /**
     * NLP2DSL2SQL Agent V2 — 语义层管线（流式）
     * 意图识别 → 向量检索+Rerank → DSL生成 → DSL校验 → DSL富化 → SQL翻译 → SQL审查+执行 → 自然语言回答
     */
    @GetMapping(value = "/nlp2Dsl2SqlAgentV2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> nlp2Dsl2SqlAgentV2(Nlp2DslAgentRequest request) {
        return semanticDslAgentService.nlp2Dsl2SqlAgentV2(request.getQuestion());
    }
}
