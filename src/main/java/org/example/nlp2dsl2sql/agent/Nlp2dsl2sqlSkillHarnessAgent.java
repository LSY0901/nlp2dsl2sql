package org.example.nlp2dsl2sql.agent;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.tools.AgentToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * HarnessAgent + Classpath Skill + SkillToolGroup 配置。
 * <p>
 * 业务工具挂在 {@code nlp2sql_query} 对应的 SkillToolGroup 上：
 * Agent 加载该 skill 后工具组才激活；会话记忆依赖 RuntimeContext 的
 * userId / sessionId。
 */
@Slf4j
@Configuration
public class Nlp2dsl2sqlSkillHarnessAgent {

    /** 与 SKILL.md frontmatter name 一致 */
    public static final String SKILL_NLP2SQL_QUERY = "nlp2sql-query";

    /** 与 SKILL.md frontmatter name 一致 */
    public static final String SKILL_CHITCHAT = "chitchat";

    /** Skill 绑定的业务工具组名 */
    public static final String GROUP_NLP2SQL_QUERY = "nlp2sql_query_tools";

    public static final String SKILL_HARNESS_PROMPT = """
            你是 NLP2DSL2SQL 数据查询系统的调度 Agent（Harness Skill 模式）。

            ## 工作方式

            1. 查看 available_skills，选择最合适的 skill
            2. 先调用 load_skill_through_path 加载该 skill 的 SKILL.md
            3. 严格按 skill 说明执行（业务查询需按工具顺序调用）
            4. 闲聊类问题加载 chitchat，直接自然语言回复

            ## 重要规则

            - 每次只调用一个工具
            - 业务数据问题必须使用 nlp2sql_query skill
            - 加载 nlp2sql_query 后才会激活业务工具组
            - 不要调用与业务无关的内置工具（文件、Shell 等）
            - 最终回答简洁，只输出结论本身
            """;

    /**
     * Skill Harness Agent Bean。
     *
     * @param model        LLM
     * @param toolRegistry 业务 @Tool 注册表
     * @return HarnessAgent
     * @throws IOException Classpath skill 加载失败
     */
    @Bean(name = "nlp2dsl2sqlSkillAgent")
    public HarnessAgent nlp2dsl2sqlSkillHarnessAgent(
            OpenAIChatModel model,
            AgentToolRegistry toolRegistry) throws IOException {
        Toolkit toolkit = new Toolkit();
        // 默认不激活：仅当加载 nlp2sql_query skill 后激活
        toolkit.createSkillToolGroup(
                GROUP_NLP2SQL_QUERY,
                "NLP2SQL 业务查询工具组（加载 nlp2sql_query skill 后可用）",
                false,
                SKILL_NLP2SQL_QUERY);
        toolkit.registration()
                .tool(toolRegistry)
                .group(GROUP_NLP2SQL_QUERY)
                .apply();

        ClasspathSkillRepository skillRepo =
                new ClasspathSkillRepository("skills");
        log.info("Classpath skills loaded: {}", skillRepo.getAllSkillNames());

        return HarnessAgent.builder()
                .name("nlp2dsl2sqlSkillAgent")
                .sysPrompt(SKILL_HARNESS_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .skillRepository(skillRepo)
                .maxIters(20)
                .workspace(Paths.get(".agentscope/workspace"))
                // SSE 中断时会话可能残留未完成的 tool_call；开启后自动补错误结果继续对话。
                .enablePendingToolRecovery(true)
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDefaultWorkspaceSkills()
                // 保留 memory hooks / session persistence，支持多轮记忆
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
