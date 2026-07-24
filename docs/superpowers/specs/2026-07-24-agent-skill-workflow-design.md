# Agent-Skill-Workflow 分层设计

> 日期：2026-07-24  
> 状态：已批准（Harness SkillToolGroup 改造已落地，见同日计划）  
> 目标：新增并行接口，采用 **Agent 选型 Skill → Workflow 按 Skill 步骤调度 → Tool/Service 执行**，  
> 复用 `nlp2Dsl2SqlAgent` 的业务能力，不改造现有三条旧接口。

## 1. 背景与目标

### 1.1 现状

| 接口 | 模式 | 问题 |
|------|------|------|
| `/aiChat/nlp2Dsl2SqlAgent` | 单 Agent + 多工具 ReAct | LLM 同时决定 What/How，易乱序、难排查 |
| `/aiChat/nlp2Dsl2SqlAgentV2` | Java 硬编码 7 阶段 | 步骤不可裁剪 |
| `/aiChat/nlp2Dsl2SqlPlannerWorkflow` | Planner 出计划 + Workflow | 无 Skill 层；步骤由 Planner 自由组合 |

### 1.2 目标

新增第四条并行路径：

- **Agent（LLM）**：根据用户问题与 `available_skills` **选择 Skill**（What skill）
- **Skill（Java 定义）**：声明适用场景、默认步骤、tool 绑定（编码工具能力）
- **Workflow（Java）**：加载选中 Skill 的步骤并调度执行（How）
- **Tool/Service（Java）**：原子能力（Do），复用现有实现

### 1.3 已确认决策

| 决策点 | 选择 |
|--------|------|
| Skill 形态 | **A. 业务 Skill**（Java `SkillDefinition`，非 AgentScope 文件系统 Skill） |
| 落地方式 | Agent 选 Skill → Workflow 跑 Skill 步骤（方案 1） |
| 接口策略 | **新增并行接口**，不改旧接口 |
| 新路径 | `GET /aiChat/nlp2Dsl2SqlAgentSkillWorkflow` |
| 首版 Skill | `nlp2sql-query`（业务查询）；非业务由 Agent 直接回答 |
| Tool 复用 | 复用现有 Pipeline Service / Tool 底层，不第三套算法 |

### 1.4 非目标（首版不做）

- 不改造 `/nlp2Dsl2SqlAgent`、`V2`、`PlannerWorkflow`
- 不引入 AgentScope `SKILL.md` / `skillRepository`（那是方案 B）
- 不做多 Skill 并行执行、不做子 Agent
- 不做步骤级 LLM 重规划（首版固定走 Skill 默认步骤；失败按步 retry/abort）
- Agent 不能发明未注册的 Skill 名

---

## 2. 架构与包结构

### 2.1 分层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| Controller | HTTP/SSE 入口；定义入参出参 | 含业务编排逻辑 |
| Agent（SkillSelector） | 调用 LLM，从注册表选 Skill | 直接调检索/SQL；发明未知 Skill |
| SkillRegistry | 注册/查询 SkillDefinition | 执行业务步骤 |
| Workflow | 按 Skill 步骤调度、重试、组装 SSE | 实现检索/JOIN 等细节 |
| ToolAdapter / Pipeline | 单步原子能力 | 决定全局步骤顺序 |

### 2.2 包结构

```
org.example.nlp2dsl2sql/
├── controller/
│   └── Nlp2Dsl2SqlAgentSkillWorkflowController.java   # 新
├── skill/
│   ├── model/
│   │   ├── SkillDefinition.java
│   │   ├── SkillStep.java
│   │   └── SkillSelectionResult.java
│   ├── SkillRegistry.java
│   ├── SkillDefinitions.java                         # 内置 skill 声明
│   └── ISkillSelectorAgent.java + impl/
├── service/
│   ├── IAgentSkillWorkflowService.java               # 新入口
│   └── impl/
│       ├── AgentSkillWorkflowServiceImpl.java
│       └── SkillWorkflowEngine.java
└── （复用）
    ├── service/pipeline/*                            # 优先复用
    └── tools/*                                       # 底层能力复用
```

### 2.3 调用关系

```
Controller
  → AgentSkillWorkflowService.run(question)
       1. SkillSelectorAgent.select(question, availableSkills)
            - skill=chitchat → 服务层直接 LLM 简答，不进 Workflow
       2. SkillRegistry.require(skillName) → SkillDefinition
            - 未注册 → 友好错误结束
       3. SkillWorkflowEngine.execute(definition, question)
            - 按 steps 顺序调度 ToolAdapter（含最后一步 answer）
            - 写回 SkillWorkflowContext
            - classify_intent=NON_BUSINESS → 中止后续业务步并简答
```

---

## 3. API

| 项 | 值 |
|----|-----|
| 路径 | `GET /aiChat/nlp2Dsl2SqlAgentSkillWorkflow` |
| 入参 | `Nlp2DslAgentRequest`（字段 `question`） |
| 出参 | `Flux<String>`，`produces = TEXT_EVENT_STREAM` |
| 约定 | Controller 必须定义入参出参，禁止用 Map 接参 |

现有接口全部保留：

- `/aiChat/nlp2Dsl2SqlAgent`
- `/aiChat/nlp2Dsl2SqlAgentV2`
- `/aiChat/nlp2Dsl2SqlPlannerWorkflow`

---

## 4. 数据模型

### 4.1 SkillDefinition

```json
{
  "name": "nlp2sql-query",
  "description": "将自然语言业务问题转为语义DSL并查询数据库，适用于指标/维度分析/明细查询。",
  "version": "1.0.0",
  "steps": [
    { "tool": "classify_intent", "retry": 0, "onFailure": "ABORT" },
    { "tool": "retrieve_metadata", "retry": 1, "onFailure": "ABORT" },
    { "tool": "generate_dsl", "retry": 1, "onFailure": "ABORT" },
    { "tool": "validate_dsl", "retry": 1, "onFailure": "ABORT" },
    { "tool": "enrich_dsl", "retry": 0, "onFailure": "ABORT" },
    { "tool": "translate_sql", "retry": 0, "onFailure": "ABORT" },
    { "tool": "review_sql", "retry": 1, "onFailure": "ABORT" },
    { "tool": "execute_sql", "retry": 0, "onFailure": "ABORT" },
    { "tool": "answer", "retry": 0, "onFailure": "ABORT" }
  ]
}
```

字段约定：

| 字段 | 说明 |
|------|------|
| `name` | Skill 唯一名（snake/kebab），Agent 只能从此集合选择 |
| `description` | 给 Agent 选型用的自然语言说明 |
| `version` | 语义版本；后续升 skill 主要改 steps/绑定 |
| `steps[].tool` | 工具名，必须在 ToolAdapter 注册表中存在 |
| `steps[].retry` | 同一步最大额外重试次数，默认 0；总尝试次数 = 1 + retry |
| `steps[].onFailure` | 首版仅支持 `ABORT`（耗尽 retry 后中断）；不做 REPLAN/SKIP |

### 4.2 SkillSelectionResult（Agent 输出）

```json
{
  "skill": "nlp2sql-query",
  "reason": "用户询问三年级数学平均分，属于业务指标查询",
  "confidence": 0.92
}
```

约定：

- `skill` 必须是注册表中的 name（含 `chitchat`）
- 未知 skill → 服务层拒绝，返回友好错误（不执行）
- `chitchat`：已注册，但 `steps` 为空；服务层短路为 LLM 简答，**不进入** SkillWorkflowEngine

### 4.3 首版内置 Skill

| name | steps | 用途 |
|------|-------|------|
| `nlp2sql-query` | 见 4.1（含 answer） | 完整 NLP→DSL→SQL 查询管线 |
| `chitchat` | 空 | 非业务闲聊；服务层短路简答 |

后续升版可新增例如 `nlp2sql-metadata-only`（只检索不执行），通过改 `SkillDefinitions` + version 完成。

### 4.4 SkillWorkflowContext

跨步骤可变状态，至少包含：

- `question`
- `selectedSkill` / `skillVersion`
- `intent` / `intentResult`
- `candidate` / `candidateContext`
- `semanticDSL` / `dslJson`
- `enrichedDSL` / `enrichedDslJson`
- `sql` / `params`
- `queryResult`
- `lastError` / `failedTool`
- `progress`（供 SSE）

可与 `AgentSessionContext` 字段对齐，但使用独立类型，避免与 ReAct RuntimeContext 耦合。

---

## 5. Tool 编码与适配

### 5.1 Tool 名（与 ReAct AgentToolRegistry 对齐）

| tool | 对应能力 | 推荐复用 |
|------|----------|----------|
| `classify_intent` | 意图识别 | `IntentTool` |
| `retrieve_metadata` | 语义检索 | `RetrievalTool` + `CandidateContextTool` |
| `generate_dsl` | DSL 生成 | `DslGenerationTool` |
| `validate_dsl` | DSL 校验 | `ValidationTool` / ValidationPipeline |
| `enrich_dsl` | DSL 富化 | `EnrichmentTool` / EnrichmentPipeline |
| `translate_sql` | SQL 翻译 | `TranslationTool` / TranslationPipeline |
| `review_sql` | SQL 审查 | `ReviewTool` / ReviewPipeline |
| `execute_sql` | SQL 执行 | `SqlExecutionTool` / SqlExecutePipeline |
| `answer` | 自然语言回答 | `AnswerPipelineService`（流式） |

### 5.2 ToolAdapter

```
SkillToolAdapter.execute(toolName, SkillWorkflowContext) → void / String summary
```

- Skill 只声明 tool **名**，不内联业务代码
- Adapter 负责从 context 取入参、调用底层、写回 context
- 未知 toolName → 立即 ABORT

### 5.3 门禁（Workflow 强制）

对 `nlp2sql-query`：

1. 若 `classify_intent` 得到 `NON_BUSINESS`：中止后续业务步，走简答（或视为应改选 chitchat）
2. `execute_sql` 前必须已有通过的 `review_sql`（或本 skill 步骤中 review 在 execute 之前且未失败）
3. `answer` 必须是最后一步（若声明）

---

## 6. 执行流与 SSE

### 6.1 主流程

```
Controller(SSE)
  → AgentSkillWorkflowService.run(question)
      1. SkillSelectorAgent.select(...)
         - skill=chitchat → LLM 简答流式返回，结束
      2. 校验 skill 已注册
      3. SkillWorkflowEngine 按 steps 执行：
         - 推送 [Skill] name@version
         - 每步 [Tool开始]/[Tool结束]
         - 失败：耗尽 retry 后按 onFailure（首版 ABORT）
      4. answer 步骤流式输出最终结论
```

### 6.2 SSE 输出约定

- `[Skill选择] {skill} ({reason})`
- `[Skill] {name}@{version}`
- `[Tool开始] {tool}` / `[Tool结束] {tool}`
- 最终自然语言结论（answer）
- 错误：`错误: ...`

### 6.3 Agent Prompt 要点

- 只输出 JSON：`{skill, reason, confidence}`
- 只能从 `available_skills` 列表选择
- 业务数据查询必须选 `nlp2sql-query`
- 问候/无关问题选 `chitchat`

---

## 7. 与现有路径关系

| 路径 | 保留 | 关系 |
|------|------|------|
| ReAct Agent | 是 | Tool 实现可被 Adapter 复用 |
| V2 Workflow | 是 | 无直接依赖 |
| PlannerWorkflow | 是 | 概念相近但无 Skill 层；本方案不合并 |

---

## 8. 实现分期

### Phase 1（首版交付）

1. `SkillDefinition` / `SkillStep` / `SkillSelectionResult` / `SkillWorkflowContext`
2. `SkillRegistry` + 内置 `nlp2sql-query` / `chitchat`
3. `SkillSelectorAgent`（LLM JSON 选型）
4. `SkillToolAdapter`（绑定 9 个 tool）
5. `SkillWorkflowEngine` + `AgentSkillWorkflowService`
6. `Nlp2Dsl2SqlAgentSkillWorkflowController`
7. 编译通过；手工 SSE 冒烟（业务问 + 闲聊）

### Phase 2（后续升 skill 版本，不在首版）

- 多版本 Skill / 元数据-only Skill
- 失败 REPLAN（回 Agent 重选或改步骤）
- AgentScope 原生 Skill 双轨（可选）

---

## 9. 测试计划

- [ ] 业务问题：Agent 选中 `nlp2sql-query`，按序执行 tool，返回结论
- [ ] 闲聊：选中 `chitchat`，不调用业务 tool
- [ ] 未知 skill：返回错误，不执行
- [ ] `validate_dsl` 失败且 retry 用尽：ABORT 友好错误
- [ ] 旧三接口行为无回归（不改代码路径）

---

## 10. 开放问题（首版已拍板默认值）

| 问题 | 默认 |
|------|------|
| validate 失败是否自动回退 generate_dsl | 否；仅本步 retry，否则 ABORT |
| classify 得到 NON_BUSINESS 时 | 中止业务步，简答结束 |
| answer 是否算 skill step | 是，作为最后一步 |
