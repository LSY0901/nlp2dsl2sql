package org.example.nlp2dsl2sql.agent;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * 多 Agent 配置 — 基于 AgentScope Harness 的多 Agent 架构。
 * <p>
 * 创建 Supervisor Agent，通过 ReAct 循环自主决策调用哪些工具、以什么顺序执行。
 */
@Configuration
public class MultiAgentConfig {

    /**
     * Supervisor Agent 的系统提示词。
     * <p>
     * Supervisor 通过 ReAct 循环自主调用工具完成 NLP→DSL→SQL 全流程。
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

            2. 如果是非业务问题，直接调用 finish 工具回答用户。

            3. 调用 retrieve_metadata 检索相关元数据（指标、维度、实体）

            4. 基于候选元数据，在推理中直接生成语义 DSL JSON：
               {"metric":"指标code","entity":"实体code","dimensions":["维度code"],"filters":[{"dimension":"维度code","value":"维度值code"}]}
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

            10. 调用 finish 工具，传入最终的自然语言回答

            ## 重要规则

            - 每次只调用一个工具
            - 仔细阅读工具返回的结果，根据结果决定下一步
            - 如果某一步失败，分析错误原因并尝试修正
            - 最终回答要简洁，只输出结论本身
            """;

    // ==================== 旧版 Agent Prompt 常量（保留兼容） ====================

    public static final String INTENT_AGENT_PROMPT = "你是意图识别专家。";
    public static final String DSL_GENERATOR_AGENT_PROMPT = "你是DSL生成专家。";
    public static final String REVIEW_AGENT_PROMPT = "你是SQL审查专家。";
    public static final String ANSWER_AGENT_PROMPT = "你是数据分析专家。";

    // ==================== Supervisor Agent Bean ====================

    /**
     * Supervisor Agent — 多 Agent 系统的核心编排者。
     * <p>
     * 通过 ReAct 循环（推理→工具调用→观察结果→再推理）自主编排全流程。
     * 工具函数定义在 {@link org.example.nlp2dsl2sql.semanticdsl.tools.MultiAgentToolRegistry}。
     */
    @Bean
    public HarnessAgent supervisorAgent(OpenAIChatModel model) {
        return HarnessAgent.builder()
                .name("Supervisor")
                .sysPrompt(SUPERVISOR_PROMPT)
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
