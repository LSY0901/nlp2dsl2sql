package org.example.nlp2dsl2sql.agent;

import org.example.nlp2dsl2sql.tools.AgentToolRegistry;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * 多 Agent 配置 — 基于 AgentScope Harness 的 nlp2dsl2sqlAgentLatest + Toolkit 架构。
 * <p>
 * nlp2dsl2sqlAgentLatest 通过框架内置 ReAct 循环调用 {@link AgentToolRegistry} 中
 * 以 {@code @Tool} 注解注册的业务工具。
 */
@Configuration
public class Nlp2dsl2sqlAgent {

    /**
     *
     * LLM负责规划、Workflow负责执行、Service负责能力
     */

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

            1. 首先推理用户问题的意图类型：
               - METRIC_QUERY: 查询指标值，如"三年级数学平均分是多少"
               - DIMENSION_ANALYSIS: 按维度分析指标，如"各年级数学平均分对比"
               - DETAIL_QUERY: 查询明细数据
               - NON_BUSINESS: 非业务问题，直接回答

            2. 如果是非业务问题，直接用自然语言回答用户，不要调用业务工具。

            3. 调用 retrieve_metadata 检索相关元数据（指标、维度、实体）

            4. 基于候选元数据，在推理中直接生成语义 DSL JSON：
               {"metric":"指标code","entity":"实体code","dimensions":["维度code"],\
            "filters":[{"dimension":"维度code","value":"维度值code"}]}
               规则：
               - metric/entity/dimensions 必须从候选元数据中选择，禁止编造
               - 「有多少/几个/数量」选 student_count 等计数指标
               - 「平均分」选 avg_score，「总成绩」选 sum_score
               - DIMENSION_ANALYSIS 必须包含 dimensions

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
            - 最终回答要简洁，只输出结论本身
            - 不要调用与业务无关的内置工具（文件、Shell、记忆等）
            """;

    /**
     * nlp2dsl2sqlAgentLatest Agent — 多 Agent 系统的核心编排者。
     * <p>
     * 将 {@link AgentToolRegistry} 注册到 Toolkit，由 HarnessAgent
     * 内置 ReAct 循环自动完成 tool_call → 执行 → observation。
     *
     * @param model        LLM 模型
     * @param toolRegistry 带 {@code @Tool} 注解的业务工具 Bean
     * @return nlp2dsl2sqlAgentLatest HarnessAgent
     */
    @Bean
    public HarnessAgent nlp2dsl2sqlAgentLatest(OpenAIChatModel model,
                                        AgentToolRegistry toolRegistry) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(toolRegistry);

        return HarnessAgent.builder()
                .name("nlp2dsl2sqlAgent")
                .sysPrompt(SUPERVISOR_PROMPT)
                .model(model)
                .toolkit(toolkit)
                // 最多 15 轮
                .maxIters(15)
                .workspace(Paths.get(".agentscope/workspace"))
                // 关闭与 NLP→SQL 无关的内置工具，避免干扰业务 ReAct。
                // 注意：不要 disableMemoryHooks —— 会话 JSONL / MEMORY 写入依赖它。
//                .disableFilesystemTools()
//                .disableShellTool()
//                .disableMemoryTools()
//                .disableSubagents()
//                .disableDynamicSubagents()
//                .disableDynamicSkills()
//                .disableDefaultWorkspaceSkills()
//                .disableWorkspaceContext()
//                .disableAtPathExpansion()
//                .disableToolsConfig()
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
