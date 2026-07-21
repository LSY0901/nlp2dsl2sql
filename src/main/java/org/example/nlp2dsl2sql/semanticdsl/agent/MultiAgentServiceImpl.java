package org.example.nlp2dsl2sql.semanticdsl.agent;

import org.example.nlp2dsl2sql.agent.MultiAgentConfig;
import org.example.nlp2dsl2sql.semanticdsl.tools.MultiAgentToolRegistry;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 协作服务实现 — 真正的 ReAct 循环。
 * <p>
 * <h3>核心区别于 V2 Workflow</h3>
 * <ul>
 *   <li><b>V2 Workflow</b>：Java 代码硬编码 7 阶段顺序，LLM 无决策权</li>
 *   <li><b>多 Agent（本实现）</b>：Supervisor Agent 通过 ReAct 循环自主决定调用哪个工具、以什么顺序执行</li>
 * </ul>
 * <p>
 * <h3>ReAct 循环流程</h3>
 * <pre>
 * 用户问题
 *   │
 *   ▼
 * ┌─────────────────────────────────────────┐
 * │ 1. Reasoning: LLM 推理下一步该做什么      │
 * │    stream(messages, toolSchemas, options) │
 * │    LLM 看到 ToolSchema 列表后自主选择      │
 * │    → 返回 ChatResponse（含 ToolUseBlock）  │
 * ├─────────────────────────────────────────┤
 * │ 2. Acting: Java 代码从 ToolUseBlock 提取   │
 * │    toolName + input，调用 ToolRegistry     │
 * │    执行对应 Java 方法                      │
 * ├─────────────────────────────────────────┤
 * │ 3. Observation: 将 ToolResultBlock 喂回    │
 * │    LLM，让它观察结果、推理下一步...        │
 * ├─────────────────────────────────────────┤
 * │ ... 循环直到 LLM 调用 finish 工具 ...     │
 * └─────────────────────────────────────────┘
 *   │
 *   ▼
 * SSE 流式输出最终回答
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentServiceImpl implements IMultiAgentService {

    private final OpenAIChatModel openAIChatModel;
    private final MultiAgentToolRegistry toolRegistry;

    /** 最大 ReAct 迭代次数，防止无限循环 */
    private static final int MAX_ITERATIONS = 15;

    @Override
    public Flux<String> multiAgentQuery(String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }

        return Flux.defer(() -> {
            try {
                return runReActLoop(question.trim());
            } catch (Exception e) {
                log.error("ReAct 循环异常", e);
                return Flux.just("错误: " + e.getMessage());
            } finally {
                toolRegistry.clearContext();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * ReAct 主循环 — LLM 自主决策调用哪个工具。
     * <p>
     * 每次迭代：
     * 1. 将历史消息 + ToolSchema 列表发给 LLM
     * 2. LLM 返回 ChatResponse，content 中可能包含 ToolUseBlock
     * 3. 如果有 ToolUseBlock → 执行工具 → 将 ToolResultBlock 加入消息 → 继续循环
     * 4. 如果是 finish 工具 → 输出最终回答
     * 5. 如果无 ToolUseBlock → 直接输出文本
     */
    private Flux<String> runReActLoop(String question) {
        log.info("━━━━━━━ ReAct 多 Agent 循环启动 ━━━━━━━");
        log.info("用户问题: {}", question);

        // 构建消息列表（ReAct 循环中持续追加）
        List<Msg> messages = new ArrayList<>();
        messages.add(Msg.builder()
                .role(MsgRole.SYSTEM)
                .textContent(MultiAgentConfig.SUPERVISOR_PROMPT)
                .build());
        messages.add(Msg.builder()
                .role(MsgRole.USER)
                .textContent(question)
                .build());

        // 构建 ToolSchema 列表 — 传给 LLM 的 tools 参数
        List<ToolSchema> toolSchemas = toolRegistry.buildToolSchemas();
        log.info("[ReAct] 注册工具数量: {}", toolSchemas.size());

        GenerateOptions options = GenerateOptions.builder()
                .temperature(0.2)
                .build();

        // ReAct 迭代
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("[ReAct] 第 {} 轮迭代", i + 1);

            // 1. Reasoning: 调用 LLM（传入消息 + 工具 schema）
            ChatResponse llmResponse = callLlmWithTools(messages, toolSchemas, options);
            log.info("[ReAct] LLM finishReason: {}", llmResponse.getFinishReason());

            // 提取 LLM 响应中的文本和 tool_use 块
            String textContent = extractText(llmResponse);
            List<ToolUseBlock> toolUseBlocks = extractToolUseBlocks(llmResponse);

            if (!textContent.isEmpty()) {
                log.info("[ReAct] LLM 文本: {}", textContent.length() > 200 ? textContent.substring(0, 200) + "..." : textContent);
            }

            // 2. 如果没有 tool_call，LLM 直接输出文本作为最终回答
            if (toolUseBlocks.isEmpty()) {
                log.info("[ReAct] 无工具调用，直接输出文本回答");
                log.info("━━━━━━━ ReAct 循环完成（{} 轮） ━━━━━━━", i + 1);
                return Flux.just(textContent);
            }

            // 3. 将 LLM 的响应（含 ToolUseBlock）加入消息历史
            messages.add(Msg.builder()
                    .role(MsgRole.ASSISTANT)
                    .content(llmResponse.getContent())
                    .build());

            // 4. 处理每个 ToolUseBlock
            for (ToolUseBlock toolUse : toolUseBlocks) {
                String toolName = toolUse.getName();
                var input = toolUse.getInput();
                log.info("[ReAct] LLM 调用工具: {} ({})", toolName, input);

                // 检查是否是 finish 工具
                if ("finish".equals(toolName)) {
                    String answer = (String) input.getOrDefault("answer", "");
                    log.info("[ReAct] finish 工具被调用");
                    log.info("━━━━━━━ ReAct 循环完成（{} 轮） ━━━━━━━", i + 1);
                    return Flux.just(answer);
                }

                // 5. Acting: 执行工具
                String toolResult = toolRegistry.executeTool(toolName, input);
                log.info("[ReAct] 工具 {} 返回: {}",
                        toolName,
                        toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult);

                // 6. Observation: 将 ToolResultBlock 加入消息历史
                messages.add(Msg.builder()
                        .role(MsgRole.SYSTEM)
                        .content(ToolResultBlock.of(
                                toolUse.getId(),
                                toolName,
                                TextBlock.builder().text(toolResult).build()
                        ))
                        .build());
            }
        }

        log.warn("ReAct 循环达到最大迭代次数 {}", MAX_ITERATIONS);
        return Flux.just("错误: 处理超时，达到最大迭代次数 " + MAX_ITERATIONS);
    }

    // ==================== LLM 调用 ====================

    /**
     * 调用 LLM，传入消息 + 工具 schema，返回 ChatResponse。
     * <p>
     * 工具 schema 通过第二个参数传给 LLM，LLM 通过 OpenAI Function Calling 自主决策。
     */
    private ChatResponse callLlmWithTools(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return openAIChatModel.stream(messages, tools, options)
                .blockLast();
    }

    // ==================== ChatResponse 解析 ====================

    /**
     * 从 ChatResponse 中提取文本内容。
     */
    private String extractText(ChatResponse response) {
        if (response.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 从 ChatResponse 中提取所有 ToolUseBlock。
     * <p>
     * 当 LLM 决定调用工具时，OpenAI 返回的 content 中会包含 ToolUseBlock，
     * 其中 {@link ToolUseBlock#getName()} 是工具名，{@link ToolUseBlock#getInput()} 是参数。
     */
    private List<ToolUseBlock> extractToolUseBlocks(ChatResponse response) {
        List<ToolUseBlock> blocks = new ArrayList<>();
        if (response.getContent() == null) {
            return blocks;
        }
        for (ContentBlock block : response.getContent()) {
            if (block instanceof ToolUseBlock tub) {
                blocks.add(tub);
            }
        }
        return blocks;
    }
}
