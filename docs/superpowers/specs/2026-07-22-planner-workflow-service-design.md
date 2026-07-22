# Planner-Workflow-Service 分层设计

> 日期：2026-07-22  
> 状态：已批准  
> 目标：在保留现有 ReAct / V2 接口的前提下，新增一套  
> **LLM（Planner）规划 What + 步骤 → Workflow 决定 How → Service 完成 Do** 的企业级分层实现。

## 1. 背景与目标

### 1.1 现状

| 接口 | 模式 | 问题 |
|------|------|------|
| `/aiChat/nlp2Dsl2SqlAgent` | 单 Agent + 多工具 ReAct | LLM 同时决定 What 与 How，流程不稳定、难排查 |
| `/aiChat/nlp2Dsl2SqlAgentV2` | Java 硬编码 7 阶段 Workflow | How 固定，无法按问题裁剪/重规划步骤 |

### 1.2 目标

新增并行接口，采用企业常见三层：

- **Planner（LLM）**：规划查询内容与执行步骤清单（What + step list）
- **Workflow（Java）**：按计划调度、失败重试/重规划（How）
- **Service（Java）**：原子业务能力（Do）

### 1.3 已确认决策

| 决策点 | 选择 |
|--------|------|
| Planner 形态 | 完整 Planner：输出结构化执行计划（步骤、skip、retry、onFailure） |
| 失败策略 | 可重规划：失败回传 Planner，最多 `maxReplan` 次（默认 2） |
| Service 落地 | 复制独立 Pipeline Service，与 ReAct Tool 解耦并行 |
| DSL 生成位置 | 独立 Service 步骤 `GENERATE_DSL`，不在 Planner 计划体中产出完整 DSL |
| 落地方式 | 包内三层 + `StepType` 枚举调度（方案 1） |

### 1.4 非目标（首版不做）

- 不改造现有 ReAct Agent / Tool / V2 Workflow
- 不引入外部工作流引擎
- 不做步骤并行、不做子 Agent
- Planner 不能发明未知 `StepType`

## 2. 架构与包结构

### 2.1 分层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| Controller | HTTP/SSE 入口；定义入参出参 | 含业务编排逻辑 |
| Planner | 调用 LLM 产出/修正 `QueryPlan` | 直接调检索/执行 SQL |
| Workflow | 校验计划、按步调度、重试/重规划、组装 SSE | 实现检索/翻译等业务细节 |
| Service | 单一步骤原子能力 | 决定全局步骤顺序 |

### 2.2 包结构

```
org.example.nlp2dsl2sql/
├── controller/
│   └── Nlp2Dsl2SqlPlannerWorkflowController.java
├── planner/
│   ├── IQueryPlanner.java
│   ├── QueryPlanner.java
│   └── model/
│       ├── QueryPlan.java
│       ├── PlanStep.java
│       ├── PlanGoal.java
│       └── StepType.java
├── workflow/
│   ├── IQueryWorkflowEngine.java
│   ├── QueryWorkflowEngine.java
│   ├── WorkflowContext.java
│   └── WorkflowException.java
└── service/pipeline/
    ├── IRetrievalPipelineService.java + Impl
    ├── IDslGeneratePipelineService.java + Impl
    ├── IValidationPipelineService.java + Impl
    ├── IEnrichmentPipelineService.java + Impl
    ├── ITranslationPipelineService.java + Impl
    ├── IReviewPipelineService.java + Impl
    ├── ISqlExecutePipelineService.java + Impl
    └── IAnswerPipelineService.java + Impl
```

### 2.3 调用关系

```
Controller → WorkflowEngine
               ├─ QueryPlanner.plan / replan
               └─ *PipelineService（按 StepType 调度）
```

底层组件（`DslRetriever`、`SemanticDslValidator`、`SemanticDslEnricher`、
`DslTranslator`、`ReviewTool`、`SqlExecuteTool`、`OpenAIChatModel` 等）由
Pipeline Service **注入复用**；复制的是面向 Workflow 的 Service 接口与适配，
不是再实现一套检索/JOIN 算法。

## 3. API

| 项 | 值 |
|----|-----|
| 路径 | `GET /aiChat/nlp2Dsl2SqlPlannerWorkflow` |
| 入参 | `Nlp2DslAgentRequest`（字段 `question`） |
| 出参 | `Flux<String>`，`produces = TEXT_EVENT_STREAM` |
| 约定 | Controller 必须定义入参出参，禁止用 Map 接参 |

现有接口全部保留：

- `/aiChat/nlp2Dsl2SqlAgent`
- `/aiChat/nlp2Dsl2SqlAgentV2`

## 4. 数据模型

### 4.1 StepType（封闭枚举）

按推荐默认顺序：

1. `RETRIEVE`
2. `GENERATE_DSL`
3. `VALIDATE`
4. `ENRICH`
5. `TRANSLATE`
6. `REVIEW`
7. `EXECUTE`
8. `ANSWER`

Planner **只能**从上述枚举中选择；未知类型由 Workflow 拒绝。

### 4.2 QueryPlan

```json
{
  "intent": "METRIC_QUERY",
  "reason": "用户询问三年级数学平均分",
  "goal": {
    "metricHint": "平均分",
    "dimensionHints": ["年级"],
    "filterHints": ["三年级", "数学"]
  },
  "steps": [
    { "type": "RETRIEVE", "skip": false, "retry": 1, "onFailure": "REPLAN" },
    { "type": "GENERATE_DSL", "skip": false, "retry": 1, "onFailure": "REPLAN" },
    { "type": "VALIDATE", "skip": false, "retry": 0, "onFailure": "REPLAN" },
    { "type": "ENRICH", "skip": false, "retry": 0, "onFailure": "ABORT" },
    { "type": "TRANSLATE", "skip": false, "retry": 0, "onFailure": "ABORT" },
    { "type": "REVIEW", "skip": false, "retry": 1, "onFailure": "REPLAN" },
    { "type": "EXECUTE", "skip": false, "retry": 0, "onFailure": "ABORT" },
    { "type": "ANSWER", "skip": false, "retry": 0, "onFailure": "ABORT" }
  ],
  "maxReplan": 2
}
```

字段约定：

| 字段 | 说明 |
|------|------|
| `intent` | `METRIC_QUERY` / `DIMENSION_ANALYSIS` / `DETAIL_QUERY` / `NON_BUSINESS` |
| `goal` | 查询目标提示，供 `GENERATE_DSL` 参考；**禁止**包含完整 `SemanticQueryDSL` |
| `steps[].type` | `StepType` 枚举名 |
| `steps[].skip` | `true` 时 Workflow 跳过该步 |
| `steps[].retry` | 同一步 Workflow 内重试次数（不含重规划），默认 0 |
| `steps[].onFailure` | 见 4.3 |
| `maxReplan` | 整次查询最大重规划次数；缺省按 2 |

`NON_BUSINESS`：`steps` 可为空；Workflow 直接返回 `reason` 友好说明并结束。

### 4.3 onFailure

| 值 | 行为 |
|----|------|
| `RETRY` | 同一步重试，次数 ≤ `retry`；用尽后按 `ABORT` |
| `REPLAN` | 先耗尽本步 `retry`（若 >0），仍失败则触发重规划 |
| `SKIP` | 跳过本步继续；**仅允许**非关键步骤（见 5.3） |
| `ABORT` | 立即中断，返回友好错误 |

### 4.4 WorkflowContext

跨步骤可变状态，至少包含：

- `question`
- `plan`（当前生效计划）
- `replanCount`
- `candidate`（`DslCandidate`）
- `semanticDSL`（`SemanticQueryDSL`）
- `enrichedDSL`（`EnrichedQueryDSL`）
- `sql` / `params`
- `queryResult`
- `lastError` / `failedStep`（供 replan）

## 5. 执行流与错误处理

### 5.1 主流程

```
Controller(SSE)
  → WorkflowEngine.run(question)
      1. Planner.plan(question) → QueryPlan
         - intent=NON_BUSINESS → 返回 reason，结束
      2. validatePlan(plan)
         - 未知 StepType → 拒绝
         - 业务 intent 缺关键步骤（见 5.3）→ 拒绝并尝试 REPLAN 一次，仍非法则 ABORT
      3. 按 plan.steps 顺序执行：
         - skip=true → 跳过
         - 调对应 Pipeline Service，写回 WorkflowContext
         - 失败 → 按 onFailure 处理
      4. 若需 REPLAN 且 replanCount < maxReplan：
         Planner.replan(question, oldPlan, failedStep, error, context摘要)
         → 新计划；replanCount++；**从步骤列表头部重新执行**
         （不保留半成品状态中的 semanticDSL/sql；保留 question；
           candidate 可按新计划是否含 RETRIEVE 决定是否清空）
      5. ANSWER 步骤产出流式文本，拼入 SSE
```

重规划后**一律从头执行新计划**（明确选定，避免半成品状态污染）。  
重规划前清空：`semanticDSL`、`enrichedDSL`、`sql`、`params`、`queryResult`。  
若新计划包含 `RETRIEVE`，同时清空 `candidate`；否则可保留上次 `candidate`。

### 5.2 SSE 输出

- 进度：`[计划] {intent}`、`[步骤] {type} 开始/完成`、`[重规划] 第 n 次`
- 最终：`ANSWER` 流式自然语言结论
- 错误：单条 `错误: {message}` 后结束

### 5.3 Workflow 强制门禁（Planner 不可绕过）

1. **未知 StepType**：拒绝计划
2. **关键步骤不可 SKIP**：`RETRIEVE`、`GENERATE_DSL`、`VALIDATE`、`ENRICH`、`TRANSLATE`、`EXECUTE`、`ANSWER`  
   仅 `REVIEW` 在计划写 `skip=true` 时仍**不允许真正跳过审查门禁**：  
   - 若计划试图 `skip=REVIEW` 或 `onFailure=SKIP` 于 REVIEW，Workflow 强制改为必须执行 REVIEW  
   - `EXECUTE` 前必须存在一次成功的 `REVIEW` 结果，否则 ABORT
3. **业务 intent**（非 `NON_BUSINESS`）计划必须至少包含：  
   `RETRIEVE`、`GENERATE_DSL`、`VALIDATE`、`ENRICH`、`TRANSLATE`、`REVIEW`、`EXECUTE`、`ANSWER`（可乱序由 Planner 排列，但缺一不可；推荐按 4.1 顺序）
4. **SQL 安全**：`SqlExecutePipelineService` 必须保持 SELECT-only + 关键字黑名单 + 参数绑定（与现有一致）

### 5.4 Planner 接口

```text
QueryPlan plan(String question)

QueryPlan replan(
    String question,
    QueryPlan previousPlan,
    StepType failedStep,
    String errorMessage,
    String contextSummary
)
```

实现：`OpenAIChatModel` + JSON Object 响应（DeepSeek 不支持 json_schema，与现有一致）。  
提示词独立常量，放在 `planner` 包或现有 prompt 类中的 Planner 专用常量。

## 6. Service 复制边界

| Pipeline Service | 能力来源（注入复用 + 适配） | 输入（概念） | 输出（写入 Context） |
|------------------|------------------------------|--------------|----------------------|
| Retrieval | `RetrievalTool` / `DslRetriever` + 候选上下文构建 | question | candidate |
| DslGenerate | V2 `generateSemanticDSL` 逻辑（独立 LLM） | question, candidate, goal, intent | semanticDSL |
| Validation | `SemanticDslValidator` / `ValidationTool` | semanticDSL, intent | 校验通过或抛错 |
| Enrichment | `SemanticDslEnricher` / `EnrichmentTool` | semanticDSL, candidate | enrichedDSL |
| Translation | `DslTranslator` / `TranslationTool` | enrichedDSL | sql, params |
| Review | `ReviewTool` | sql, enrichedDSL, question | 审查通过或抛错 |
| SqlExecute | `SqlExecuteTool` / `SqlExecutionTool` | sql, params | queryResult |
| Answer | V2 `streamLlmAnswer` | question, sql, queryResult | `Flux<String>` |

说明：

- 不依赖 `AgentToolRegistry` / `@Tool` / `AgentSessionContext`
- 每个 Service 方法加注释；单方法不超过 300 行；单行不超过 100 字符
- 编码风格与现有项目一致（构造器注入、`@Slf4j`）

> 说明：用户规则中有 Java 8 / Spring Boot 2.x 约定，但本仓库实际为 Java 21 + Spring Boot 3.x；  
> **本设计实现遵循仓库现有技术栈**，不降级版本。

## 7. 测试要点

1. Planner 返回合法 JSON，业务 intent 含完整关键步骤
2. `NON_BUSINESS` 短路，不调用业务 Service
3. 某步失败触发 replan，且不超过 `maxReplan`
4. 未知 StepType / 缺 REVIEW 即 EXECUTE → Workflow 拒绝
5. 试图 skip REVIEW → 被门禁纠正或 ABORT
6. 新接口与 ReAct / V2 可并行调用、互不影响

## 8. 实现顺序建议

1. `planner/model` 数据类 + `StepType` / `onFailure` 枚举
2. Pipeline Service 接口与实现（先适配底层组件）
3. `QueryPlanner`（plan + replan）
4. `QueryWorkflowEngine` + `WorkflowContext`
5. `Nlp2Dsl2SqlPlannerWorkflowController`
6. 联调与日志（`━━━` 阶段分隔，与现有风格一致）

## 9. 成功标准

- 新 SSE 接口可完成与 V2 同类业务查询
- 日志中可清晰看到：计划内容 → 逐步执行 →（可选）重规划 → 回答
- 现有 `/nlp2Dsl2SqlAgent` 与 `/nlp2Dsl2SqlAgentV2` 行为无回归
