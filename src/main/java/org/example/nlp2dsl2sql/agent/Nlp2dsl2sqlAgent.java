package org.example.nlp2dsl2sql.agent;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import org.example.nlp2dsl2sql.service.memory.impl.SpringRedisAgentStateStore;
import org.example.nlp2dsl2sql.tools.AgentToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多 Agent 配置 — 基于 AgentScope Harness 的 nlp2dsl2sqlAgentLatest + Toolkit 架构。
 * <p>
 * 彻底禁用本地 Workspace（{@code MEMORY.md} / {@code sessions/*.jsonl}）与内置 compaction，
 * 短期会话状态统一接入 {@link SpringRedisAgentStateStore}。
 */
@Configuration
public class Nlp2dsl2sqlAgent {

    /** Agent 名，同时作为 Redis 状态键命名空间 */
    public static final String AGENT_NAME = "nlp2dsl2sqlAgent";

    /**
     * nlp2dsl2sqlAgentLatest Agent 的系统提示词。
     * <p>
     * nlp2dsl2sqlAgentLatest 通过 ReAct 循环自主调用工具完成 NLP→DSL→SQL 全流程。
     * LLM 决定调用顺序，而非 Java 代码硬编码。
     */
    public static final String SUPERVISOR_PROMPT = """
            你是 NLP2DSL2SQL 数据查询系统的总调度 Agent。

            你的职责是接收用户的自然语言问题，通过调用工具完成以下流程：

            ## 工具调用顺序（你可以根据实际情况调整）

            1. 调用 classify_intent 识别意图类型：
               - METRIC_QUERY: 查询指标值，如"三年级数学平均分是多少"
               - DIMENSION_ANALYSIS: 按维度分析指标，如"各年级数学平均分对比"
               - DETAIL_QUERY: 查询明细数据
               - NON_BUSINESS: 非业务问题

            2. 如果是 NON_BUSINESS，直接用自然语言回答用户，不要再调用业务工具。

            3. 调用 retrieve_metadata 检索相关元数据（指标、维度、实体）

            4. 调用 generate_dsl 生成语义 DSL（传入用户问题与意图类型；
               候选元数据优先从会话上下文读取，也可显式传入）

            5. 调用 validate_dsl 校验 DSL（传入 DSL JSON 和意图类型）

            6. 调用 enrich_dsl 富化 DSL（传入 DSL JSON 和用户问题）

            7. 调用 translate_sql 翻译为 SQL（传入富化后 DSL JSON）

            8. 调用 review_sql 审查 SQL（传入 SQL 和富化后 DSL JSON）

            9. 调用 execute_sql 执行查询（传入 SQL 和参数列表）

            10. 基于查询结果，直接用自然语言给出最终回答（不要再调用工具）

            ## 重要规则

            - 每次只调用一个工具
            - 仔细阅读工具返回的结果，根据结果决定下一步
            - 如果某一步失败，分析错误原因并尝试修正
              （如 validate_dsl 失败可修正后重调 generate_dsl / validate_dsl）
            - 最终回答要简洁，只输出结论本身
            - 不要调用与业务无关的内置工具（文件、Shell、记忆等）
            """;

    /**
     * nlp2dsl2sqlAgentLatest Agent — 多 Agent 系统的核心编排者。
     *
     * @param model        LLM 模型
     * @param toolRegistry 带 {@code @Tool} 注解的业务工具 Bean
     * @param stateStore   Redis 短期会话状态存储
     * @return nlp2dsl2sqlAgentLatest HarnessAgent
     */
    @Bean
    public HarnessAgent nlp2dsl2sqlAgentLatest(
            OpenAIChatModel model,
            AgentToolRegistry toolRegistry,
            SpringRedisAgentStateStore stateStore) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(toolRegistry);

        return HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SUPERVISOR_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .maxIters(15)
                // 按 Agent 名隔离 Redis 状态键，避免与同 sessionId 的 Host/Skill 模式互相恢复消息
                .stateStore(stateStore.forAgent(AGENT_NAME))
                // SSE 中断时会话可能残留未完成的 tool_call；开启后自动补错误结果继续对话。
                .enablePendingToolRecovery(true)
                // 彻底关闭本地 Workspace 文件工具、本地记忆钩子与本地会话落盘
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableCompaction()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableAtPathExpansion()
                .disableToolsConfig()
                .build();
    }
}
