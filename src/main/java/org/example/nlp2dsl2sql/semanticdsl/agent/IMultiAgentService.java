package org.example.nlp2dsl2sql.semanticdsl.agent;

import reactor.core.publisher.Flux;

/**
 * 多 Agent 协作服务接口。
 * <p>
 * 基于 AgentScope Harness 的多 Agent 架构，将原 V2 Workflow 的 7 阶段管线
 * 重新设计为「LLM Agent + 确定性工具函数」的协作模式。
 * <p>
 * 编排流程：
 * <pre>
 * 用户问题
 *   │
 *   ├─ [LLM Agent]  IntentAgent      → 意图识别（HarnessAgent，有会话记忆）
 *   │
 *   ├─ [Tool]       RetrievalTool    → 语义检索（向量召回 + Rerank，确定性）
 *   │
 *   ├─ [LLM Agent]  DslGeneratorAgent → 语义 DSL 生成（HarnessAgent，有会话记忆）
 *   │
 *   ├─ [Tool]       ValidationTool   → DSL 校验（确定性）
 *   │
 *   ├─ [Tool]       EnrichmentTool   → DSL 富化（BFS JOIN，确定性）
 *   │
 *   ├─ [Tool]       TranslationTool  → SQL 翻译（确定性）
 *   │
 *   ├─ [LLM Agent]  ReviewAgent      → SQL 审查（HarnessAgent，有会话记忆）
 *   │
 *   ├─ [Tool]       SqlExecutionTool → SQL 执行（安全 SELECT，确定性）
 *   │
 *   └─ [LLM Agent]  AnswerAgent     → 自然语言回答（HarnessAgent，流式输出）
 * </pre>
 *
 * @see org.example.nlp2dsl2sql.semanticdsl.agent.MultiAgentServiceImpl
 */
public interface IMultiAgentService {

    /**
     * 多 Agent 协作查询（SSE 流式输出）。
     *
     * @param question 用户自然语言问题
     * @return SSE 流式响应
     */
    Flux<String> multiAgentQuery(String question);
}
