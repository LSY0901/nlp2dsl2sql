package org.example.nlp2dsl2sql.service;

import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import reactor.core.publisher.Flux;

public interface ISemanticDslAgentService {

    /**
     * NLP2DSL2SQL Agent V2 — 语义层管线（流式）
     * 意图识别 → 向量检索+Rerank → DSL生成 → DSL校验 → DSL富化 → SQL翻译 → SQL审查+执行 → 自然语言回答
     *
     * @param sessionId 会话 ID，用于多轮追问消歧；为空则不做上下文补全
     * @param question  用户问题
     */
    Flux<String> nlp2Dsl2SqlAgentV2(String sessionId, String question);

    IntentResult classifyIntent(String question);

}
