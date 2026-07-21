# NLP2DSL2SQL 多 Agent 架构设计

> 将当前 `nlp2Dsl2SqlAgentV2` 的 7 阶段 workflow 管线，重新设计为基于 AgentScope Harness 的多 Agent 协作架构。

---

## 一、现状分析

### 1.1 当前 Workflow 实现特点

当前 `SemanticDslAgentServiceImpl#nlp2Dsl2SqlAgentV2` 是一个**过程式编排器**：

```
用户问题
  │  SemanticDslAgentServiceImpl（一个 Java Service Bean）
  ▼
  手动串联 7 个阶段，每个阶段直接调用 OpenAIChatModel.stream() 或 Spring 组件
  │
  ▼
Flux<String> SSE 输出
```

**问题**：
- 没有 Agent 自主性：执行顺序是硬编码的 `if/else` + 方法调用链
- 没有利用 HarnessAgent 的能力：会话管理、记忆压缩、工具调用循环全部被绕过
- LLM 只是"无状态的函数调用器"：每次 `callLlm()` 都是无上下文的一次性请求
- 扩展困难：新增阶段需要修改编排器代码

### 1.2 HarnessAgent 已验证的能力

从 `.agentscope/workspace` 的 session 日志中确认，Java HarnessAgent **已支持**：

| 能力 | 证据 |
|------|------|
| **工具调用（ReAct 循环）** | session 中出现 `[tool_call: memory_save(...)]`、`[tool_call: list_files(...)]` 等工具调用 |
| **工具结果回传** | `role: TOOL` 消息 + `toolCallId` 关联 |
| **多轮对话记忆** | 同一 `sessionId` 下多轮对话可记住上下文 |
| **记忆压缩** | `CompactionConfig`（30 轮触发，保留 10 轮） |
| **Session 持久化** | `.agentscope/workspace/{user}/agents/{agent}/sessions/*.jsonl` |
| **内置工具** | `memory_save`、`list_files`、`read_file`、`execute`、`glob_files` |

**关键 API**：
```java
// 创建 Agent
HarnessAgent agent = HarnessAgent.builder()
    .name("xxx")
    .sysPrompt("xxx")
    .model(openAIChatModel)
    .workspace(Paths.get(".agentscope/workspace"))
    .compaction(CompactionConfig.builder()...build())
    .build();

// 调用 Agent（带会话上下文）
RuntimeContext ctx = RuntimeContext.builder()
    .sessionId("xxx")
    .userId("xxx")
    .build();
agent.call(new UserMessage(question), ctx);

// 底层模型支持工具参数
openAIChatModel.stream(messages, tools, options);  // 第二个参数 = 工具列表
```

---

## 二、目标架构：Orchestrator-Worker 多 Agent 模式

参考 AgentScope 官方的 **Handoffs（Orchestrator-Workers）** 工作流模式，将 7 阶段管线拆分为多个专家 Agent，由一个 Supervisor Agent 通过工具调用进行编排。

### 2.1 架构总览

```
                           用户问题
                              │
                              ▼
                    ┌─────────────────┐
                    │  Supervisor     │  ← HarnessAgent（ReAct 循环 + 工具调用）
                    │  Agent          │     自主决策调用顺序
                    │  (编排者)       │
                    └────────┬────────┘
                             │ 通过 tool_call 委托
           ┌─────────────────┼───────────────────────────────┐
           ▼                 ▼                 ▼               ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ Intent Agent │  │Retrieval Agent│  │  DSL Agent   │  │ Review Agent │
   │ (意图识别)    │  │ (语义检索)     │  │ (DSL生成)    │  │ (SQL审查)     │
   │ HarnessAgent │  │ 工具函数组     │  │ HarnessAgent │  │ HarnessAgent │
   └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
                                            │
                         ┌──────────────────┼──────────────────┐
                         ▼                  ▼                  ▼
                 ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
                 │Validator Agent│  │Enricher Agent │  │Translator    │
                 │ (DSL校验)     │  │ (DSL富化)     │  │Agent(SQL生成) │
                 │ 工具函数       │  │ 工具函数组     │  │ 工具函数       │
                 └──────────────┘  └──────────────┘  └──────────────┘
                                                                    │
                                                                    ▼
                                                           ┌──────────────┐
                                                           │ Executor     │
                                                           │ Agent        │
                                                           │ (执行+回答)   │
                                                           │ HarnessAgent │
                                                           └──────────────┘
```

### 2.2 Agent 角色定义

| # | Agent 名称 | 类型 | 职责 | 对应原 Stage |
|---|-----------|------|------|-------------|
| 0 | **Supervisor** | HarnessAgent | 接收用户问题，通过 ReAct 工具调用编排全流程，汇总结果 | 编排器 |
| 1 | **Intent Agent** | HarnessAgent | LLM 意图分类：METRIC_QUERY / DIMENSION_ANALYSIS / DETAIL_QUERY / NON_BUSINESS | Stage 1 |
| 2 | **Retrieval Agent** | 工具函数组 | BGE-M3 向量召回 + 同义词扩展 + Rerank 精排 | Stage 2 |
| 3 | **DSL Generator Agent** | HarnessAgent | LLM 基于候选元数据生成 SemanticQueryDSL | Stage 3 |
| 4 | **Validator Agent** | 工具函数 | 校验 metric/entity/dimension/filter 合法性 | Stage 4 |
| 5 | **Enricher Agent** | 工具函数组 | 语义 code → 物理表/列，BFS JOIN 路径求解 | Stage 5 |
| 6 | **Translator Agent** | 工具函数 | EnrichedQueryDSL → 参数化 SQL | Stage 6 |
| 7 | **Review Agent** | HarnessAgent | LLM 审查 SQL 正确性 | Stage 7a |
| 8 | **Executor Agent** | HarnessAgent | 安全执行 SQL + 流式生成自然语言回答 | Stage 7b |

### 2.3 分类依据：HarnessAgent vs 工具函数

| 归类 | 判定标准 | 示例 |
|------|---------|------|
| **HarnessAgent** | 需要 LLM 推理能力、需要独立 system prompt、需要会话记忆 | 意图识别、DSL 生成、SQL 审查、自然语言回答 |
| **工具函数** | 纯确定性逻辑（向量计算、BFS 图搜索、SQL 拼接）、无需 LLM 参与 | 语义检索、DSL 校验、DSL 富化、SQL 翻译 |

> **设计原则**：LLM 参与的环节用独立 HarnessAgent（发挥 Agent 自主性 + 记忆能力），确定性逻辑封装为工具函数（保证可靠性 + 性能）。

---

## 三、详细设计

### 3.1 Supervisor Agent（编排者）

**角色**：整个多 Agent 系统的入口，接收用户问题，自主决策调用哪些工具/Agent，最终汇总结果。

```java
@Configuration
public class MultiAgentConfig {

    @Bean
    public HarnessAgent supervisorAgent(OpenAIChatModel model) {
        return HarnessAgent.builder()
            .name("Supervisor")
            .sysPrompt("""
                你是 NLP2DSL2SQL 数据查询系统的总调度 Agent。

                你的职责是接收用户的自然语言问题，通过调用工具完成以下流程：
                1. 调用 classify_intent 识别用户意图
                2. 如果是非业务问题，直接回答用户
                3. 调用 retrieve_metadata 检索相关元数据
                4. 调用 generate_dsl 生成语义 DSL
                5. 调用 validate_dsl 校验 DSL
                6. 调用 enrich_dsl 富化 DSL
                7. 调用 translate_sql 翻译为 SQL
                8. 调用 review_sql 审查 SQL
                9. 调用 execute_sql 执行查询
                10. 基于查询结果用自然语言回答用户

                你必须按顺序调用这些工具。如果某一步失败，直接告诉用户错误原因。
                每次只调用一个工具，等待结果后再决定下一步。
                """)
            .model(model)
            .workspace(Paths.get(".agentscope/workspace"))
            .compaction(CompactionConfig.builder()
                .triggerMessages(30)
                .keepMessages(10)
                .build())
            .build();
    }
}
```

**核心要点**：
- Supervisor 拥有**所有阶段工具**，通过 ReAct 循环自主调用
- 每个工具函数返回结构化结果，Supervisor 根据结果决定下一步
- 如果意图识别为 `NON_BUSINESS`，Supervisor 可以直接回答而跳过后续步骤
- Session 管理让 Supervisor 记住之前的多轮对话

### 3.2 工具函数设计（Supervisor 的 Toolkit）

Supervisor 通过以下工具函数编排全流程。每个工具函数封装了对应的业务逻辑：

#### 3.2.1 `classify_intent` — 意图识别工具

```java
@Component
@RequiredArgsConstructor
public class IntentTool {

    private final OpenAIChatModel openAIChatModel;
    private final ObjectMapper objectMapper;

    /**
     * 识别用户问题的意图类型。
     *
     * @param question 用户的自然语言问题
     * @return 意图识别结果 JSON（intent, confidence, reason）
     */
    public String classifyIntent(String question) {
        // 复用 SemanticPromptTemplates.INTENT_SYSTEM_PROMPT
        // 调用 LLM，返回 IntentResult JSON
    }
}
```

#### 3.2.2 `retrieve_metadata` — 语义检索工具

```java
@Component
@RequiredArgsConstructor
public class RetrievalTool {

    private final DslRetriever dslRetriever;

    /**
     * 向量召回 + 同义词扩展 + Rerank，返回候选元数据。
     *
     * @param question 用户问题
     * @return 候选元数据摘要（指标、维度、实体列表）
     */
    public String retrieveMetadata(String question) {
        DslCandidate candidate = dslRetriever.retrieve(question);
        // 序列化为 JSON/文本摘要返回给 Supervisor
    }
}
```

#### 3.2.3 `generate_dsl` — DSL 生成工具

```java
@Component
@RequiredArgsConstructor
public class DslGenerationTool {

    private final OpenAIChatModel openAIChatModel;
    private final ObjectMapper objectMapper;

    /**
     * 基于用户问题、意图和候选元数据，生成语义 DSL。
     *
     * @param question    用户问题
     * @param intent      意图类型
     * @param candidates  候选元数据摘要
     * @return SemanticQueryDSL JSON
     */
    public String generateDsl(String question, String intent, String candidates) {
        // 复用 SemanticPromptTemplates.DSL_GENERATION_SYSTEM_PROMPT
        // 调用 LLM，返回 SemanticQueryDSL JSON
    }
}
```

#### 3.2.4 `validate_dsl` — DSL 校验工具

```java
@Component
@RequiredArgsConstructor
public class ValidationTool {

    private final SemanticDslValidator validator;
    private final ObjectMapper objectMapper;

    /**
     * 校验 DSL 的合法性与兼容性。
     *
     * @param dslJson 语义 DSL JSON
     * @param intent  意图类型
     * @return 校验结果（valid, errors）
     */
    public String validateDsl(String dslJson, String intent) {
        SemanticQueryDSL dsl = objectMapper.readValue(dslJson, SemanticQueryDSL.class);
        ValidationResult result = validator.validate(dsl, IntentResult.parseIntentType(intent));
        return JSON.toJSONString(result);
    }
}
```

#### 3.2.5 `enrich_dsl` — DSL 富化工具

```java
@Component
@RequiredArgsConstructor
public class EnrichmentTool {

    private final SemanticDslEnricher enricher;
    private final DslRetriever retriever;
    private final ObjectMapper objectMapper;

    /**
     * 将语义 DSL 中的 code 转换为物理表/列，求解 JOIN 路径。
     *
     * @param dslJson       语义 DSL JSON
     * @param candidatesJson 候选元数据 JSON
     * @return 富化后 DSL JSON
     */
    public String enrichDsl(String dslJson, String candidatesJson) {
        // 反序列化 → enrich → 序列化返回
    }
}
```

#### 3.2.6 `translate_sql` — SQL 翻译工具

```java
@Component
@RequiredArgsConstructor
public class TranslationTool {

    private final DslTranslator dslTranslator;
    private final ObjectMapper objectMapper;

    /**
     * 将富化后的 DSL 翻译为参数化 SQL。
     *
     * @param enrichedDslJson 富化 DSL JSON
     * @return SQL + 参数 JSON
     */
    public String translateSql(String enrichedDslJson) {
        EnrichedQueryDSL dsl = objectMapper.readValue(enrichedDslJson, EnrichedQueryDSL.class);
        DslTranslator.TranslatedSql translated = dslTranslator.translate(dsl);
        return JSON.toJSONString(Map.of("sql", translated.sql(), "params", translated.parameters()));
    }
}
```

#### 3.2.7 `review_sql` — SQL 审查工具

```java
@Component
@RequiredArgsConstructor
public class ReviewTool {

    private final OpenAIChatModel openAIChatModel;

    /**
     * LLM 审查 SQL 正确性。
     *
     * @param sql    待审查的 SQL
     * @param schema 上下文 schema 摘要
     * @return 审查结果 JSON（result, reason）
     */
    public String reviewSql(String sql, String schema) {
        // 复用现有 ReviewTool 逻辑
    }
}
```

#### 3.2.8 `execute_sql` — SQL 执行工具

```java
@Component
@RequiredArgsConstructor
public class ExecutionTool {

    private final SqlExecuteTool sqlExecuteTool;

    /**
     * 安全执行 SQL（仅 SELECT），返回查询结果。
     *
     * @param sql    参数化 SQL
     * @param params 参数列表 JSON
     * @return 查询结果 JSON
     */
    public String executeSql(String sql, String paramsJson) {
        List<Object> params = JSON.parseArray(paramsJson);
        List<Map<String, Object>> result = sqlExecuteTool.executeSql(sql, params);
        return JSON.toJSONString(result);
    }
}
```

### 3.3 Controller 层设计

```java
@RestController
@RequestMapping("/aiChat")
public class MultiAgentController {

    private final HarnessAgent supervisorAgent;

    public MultiAgentController(HarnessAgent supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    /**
     * 多 Agent 协作接口（SSE 流式）
     * Supervisor Agent 自主编排意图识别→检索→DSL→校验→富化→SQL→审查→执行→回答
     */
    @GetMapping(value = "/nlp2Dsl2SqlMultiAgent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> multiAgentChat(Nlp2DslAgentRequest request) {
        String question = request.getQuestion();
        RuntimeContext ctx = RuntimeContext.builder()
            .sessionId("multi-agent-" + UUID.randomUUID())
            .userId("multi-agent-user")
            .build();

        return Flux.defer(() -> {
            // Supervisor Agent 自主调用工具编排全流程
            // 最终自然语言回答通过 stream 流式输出
            return streamAgentResponse(supervisorAgent, question, ctx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<String> streamAgentResponse(HarnessAgent agent, String question, RuntimeContext ctx) {
        // 调用 supervisor agent，获取流式响应
        // supervisor 内部通过 ReAct 循环调用各工具
    }
}
```

### 3.4 目录结构设计

```
src/main/java/org/example/nlp2dsl2sql/
├── agent/
│   ├── Nlp2dslAgent.java                    # 原有基础 Agent Bean
│   └── MultiAgentConfig.java                # ★ 多 Agent 配置（Supervisor + 专家 Agent）
├── controller/
│   ├── Nlp2Dsl2SqlAgentController.java       # 原有 V2 workflow 接口
│   └── MultiAgentController.java            # ★ 多 Agent 接口
├── semanticdsl/
│   ├── agent/
│   │   ├── ISemanticDslAgentService.java     # 原有 workflow 接口
│   │   └── SemanticDslAgentServiceImpl.java  # 原有 workflow 实现
│   ├── tools/                                # ★ 多 Agent 工具函数
│   │   ├── IntentTool.java                  #   意图识别工具
│   │   ├── RetrievalTool.java               #   语义检索工具
│   │   ├── DslGenerationTool.java           #   DSL 生成工具
│   │   ├── ValidationTool.java             #   DSL 校验工具
│   │   ├── EnrichmentTool.java             #   DSL 富化工具
│   │   ├── TranslationTool.java            #   SQL 翻译工具
│   │   ├── SqlReviewTool.java              #   SQL 审查工具
│   │   └── SqlExecutionTool.java           #   SQL 执行工具
│   └── ...（原有模块保持不变）
└── tools/
    ├── ReviewTool.java                      # 原有（可被 SqlReviewTool 复用）
    └── SqlExecuteTool.java                  # 原有（可被 SqlExecutionTool 复用）
```

---

## 四、与原 Workflow 的对比

| 维度 | V2 Workflow（现状） | 多 Agent（新设计） |
|------|-------------------|------------------|
| **编排方式** | Java 代码硬编码 if/else 链 | Supervisor Agent 通过 ReAct 自主决策 |
| **LLM 调用** | 直接 `openAIChatModel.stream()` 无上下文 | HarnessAgent 有会话记忆 + 记忆压缩 |
| **扩展性** | 新增阶段需改编排器代码 | 新增工具函数即可，Supervisor 自动感知 |
| **错误处理** | `PipelineException` 中断 | Supervisor 可自主重试或换策略 |
| **Agent 自主性** | 无（纯过程式） | 有（LLM 决策调用顺序） |
| **可观测性** | `log.info` 日志 | AgentScope Studio 可视化 + session 日志 |
| **会话管理** | 无（每次请求独立） | RuntimeContext + Session 持久化 |
| **确定性保证** | 高（代码控制流程） | 中（LLM 决策可能有偏差，需 prompt 约束） |

---

## 五、实现路线图

### Phase 1：工具函数封装（复用现有业务逻辑）

将 `SemanticDslAgentServiceImpl` 中的 7 个阶段逻辑拆分为 8 个独立工具组件：

```
1. IntentTool.classifyIntent(question)           ← 复用 classifyIntent() 方法
2. RetrievalTool.retrieveMetadata(question)      ← 复用 dslRetriever.retrieve()
3. DslGenerationTool.generateDsl(question, ...)  ← 复用 generateSemanticDSL() 方法
4. ValidationTool.validateDsl(dslJson, intent)   ← 复用 dslValidator.validate()
5. EnrichmentTool.enrichDsl(dslJson, ...)        ← 复用 dslEnricher.enrich()
6. TranslationTool.translateSql(enrichedDslJson) ← 复用 dslTranslator.translate()
7. SqlReviewTool.reviewSql(sql, schema)          ← 复用 reviewTool.reviewSql()
8. SqlExecutionTool.executeSql(sql, params)      ← 复用 sqlExecuteTool.executeSql()
```

**要点**：每个工具函数输入/输出均为 String（JSON），便于 Supervisor Agent 在消息中传递。

### Phase 2：Supervisor Agent 配置

- 编写 `MultiAgentConfig.java`，创建 Supervisor HarnessAgent
- 注册 8 个工具函数到 Supervisor 的 Toolkit
- 编写 Supervisor 的 system prompt（含工具使用说明）

### Phase 3：Controller + SSE 流式

- 编写 `MultiAgentController.java`
- 实现 `/aiChat/nlp2Dsl2SqlMultiAgent` SSE 接口
- 将 Supervisor 的流式输出转换为 `Flux<String>`

### Phase 4：前端适配

- 在 `nlp2dsl2sqlV2.html` 中新增接口切换按钮
- 或新建 `multiAgent.html` 页面

---

## 六、关键设计决策

### 6.1 为什么用「工具函数」而非「独立 HarnessAgent」做专家？

| 方案 | 优点 | 缺点 |
|------|------|------|
| **A. 工具函数（推荐）** | 实现简单、性能好、确定性高、复用现有代码 | 专家 Agent 无独立记忆 |
| B. 独立 HarnessAgent | 每个专家有独立 session 和记忆 | 调用链路长、性能差、状态管理复杂 |

**选择 A 的理由**：
- 意图识别、DSL 生成等 LLM 环节，实际上不需要跨轮记忆（每次查询是独立的）
- 确定性环节（检索、校验、富化、翻译）无需 LLM 参与，封装为工具更可靠
- Supervisor Agent 本身已有会话记忆，可以记住用户的多轮查询历史

### 6.2 为什么保留原 V2 接口？

- V2 是**确定性 workflow**，适合需要稳定行为的场景
- 多 Agent 接口是**探索性**的，适合展示 Agent 自主决策能力
- 两者共存可以对比效果，逐步迭代

### 6.3 如何保证 Supervisor 按正确顺序调用工具？

三种保障机制：
1. **System Prompt 约束**：明确告诉 Supervisor 必须按 1→8 顺序调用
2. **工具参数依赖**：`generate_dsl` 需要 `intent` 输入，`enrich_dsl` 需要 `dsl` 输入，自然形成依赖链
3. **工具返回值携带上下文**：每个工具返回 JSON 中包含 `next_step` 提示

### 6.4 SSE 流式如何实现？

Supervisor Agent 的 `call()` 返回的是完整结果。要实现 SSE 流式，有两种方式：

**方式一：Agent 流式输出**（推荐）
```java
// Supervisor 完成工具调用后，最后一步自然语言回答用 stream
openAIChatModel.stream(messages, List.of(), null)
    .map(this::extractText)
    .filter(s -> s != null && !s.isEmpty());
```

**方式二：阶段事件推送**
```java
// 每个工具调用完成后，推送一条阶段进度事件
Flux.concat(
    Flux.just("🔍 正在识别意图...\n"),
    Flux.just("📊 正在检索元数据...\n"),
    Flux.just("📝 正在生成 DSL...\n"),
    // ... 最终回答流式
    streamLlmAnswer(answerPrompt)
)
```

---

## 七、API 端点对照

| 端点 | 方式 | 说明 |
|------|------|------|
| `/aiChat/nlp2Dsl2SqlAgentV2` | GET (SSE) | **原有**：7 阶段 Workflow 管线（过程式） |
| `/aiChat/nlp2Dsl2SqlMultiAgent` | GET (SSE) | **新增**：多 Agent 协作（Supervisor + 工具编排） |
| `/agent/chat` | GET | **原有**：基础 Agent 对话 |

---

## 八、总结

```
                    ┌──────────────────────────────────────────┐
                    │          多 Agent 设计核心思想             │
                    └──────────────────────────────────────────┘

  Workflow（现状）                    Multi-Agent（新设计）
  ┌───────────────┐                  ┌───────────────────────┐
  │ Java 代码编排   │                  │ Supervisor Agent 编排  │
  │ if/else 链     │      ────►       │ ReAct + 工具调用       │
  │ 无 Agent 记忆  │                  │ 会话记忆 + 压缩        │
  │ 无自主决策     │                  │ 自主决策调用顺序        │
  └───────────────┘                  └───────────────────────┘

  业务逻辑：100% 复用（DslRetriever / SemanticDslEnricher / DslTranslator 等）
  新增内容：工具函数封装层 + Supervisor Agent 配置 + Controller
```

**核心价值**：
1. **发挥 AgentScope Harness 能力**：会话管理、记忆压缩、ReAct 工具调用循环
2. **Agent 自主性**：Supervisor 可根据上下文灵活调整策略（如非业务问题直接回答）
3. **可扩展性**：新增阶段只需新增工具函数 + 更新 prompt，无需改编排逻辑
4. **业务复用**：所有业务逻辑（检索/富化/翻译/执行）100% 复用现有组件
