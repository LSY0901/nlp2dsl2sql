package org.example.nlp2dsl2sql.service;

import reactor.core.publisher.Flux;

/**
 * 多 Agent 协作服务接口。
 * <p>
 * 基于 AgentScope HarnessAgent + {@code @Tool} Toolkit：
 * Supervisor 通过框架内置 ReAct 循环调用业务工具，完成自然语言到 SQL 查询。
 * <p>
 * 编排流程（由 Supervisor 自主决策工具顺序）：
 * <pre>
 * 用户问题
 *   │
 *   ├─ [Tool] classify_intent    → 意图识别
 *   ├─ [Tool] retrieve_metadata  → 语义检索（向量 + Rerank）
 *   ├─ [Tool] generate_dsl       → 语义 DSL 生成
 *   ├─ [Tool] validate_dsl       → DSL 校验
 *   ├─ [Tool] enrich_dsl         → DSL 富化（BFS JOIN）
 *   ├─ [Tool] translate_sql      → SQL 翻译
 *   ├─ [Tool] review_sql         → SQL 审查
 *   ├─ [Tool] execute_sql        → SQL 安全执行
 *   └─ [LLM]  Supervisor 回答    → 自然语言结论
 * </pre>
 *
 */
public interface INlp2dsl2sqlAgentService {

    /**
     * 多 Agent 协作查询（SSE 流式输出）。
     *
     * @param question 用户自然语言问题
     * @return SSE 流式响应
     */
    Flux<String> nlp2dsl2sqlAgentQuery(String question);
}
