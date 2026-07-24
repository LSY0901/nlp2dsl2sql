# Agent-Skill-Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增并行接口 `/aiChat/nlp2Dsl2SqlAgentSkillWorkflow`，实现 Agent 选型 Skill → Workflow 按 Skill 步骤调度 Tool。

**Architecture:** Java `SkillDefinition` 编码 tool 步骤；`SkillSelectorAgent`（LLM）从注册表选型；`SkillWorkflowEngine` 按步调用 `SkillToolAdapter`（复用现有 IntentTool / RetrievalTool / DslGenerationTool 等）；Controller 仅做 SSE 入口。

**Tech Stack:** Spring Boot 3、AgentScope OpenAIChatModel、Reactor Flux SSE、现有 tools/pipeline

**Spec:** `docs/superpowers/specs/2026-07-24-agent-skill-workflow-design.md`

---

## File Structure

```
src/main/java/org/example/nlp2dsl2sql/
├── controller/Nlp2Dsl2SqlAgentSkillWorkflowController.java
├── skill/
│   ├── model/SkillDefinition.java
│   ├── model/SkillStep.java
│   ├── model/SkillSelectionResult.java
│   ├── model/SkillWorkflowContext.java
│   ├── SkillRegistry.java
│   ├── SkillDefinitions.java
│   ├── ISkillSelectorAgent.java
│   ├── impl/SkillSelectorAgent.java
│   └── adapter/SkillToolAdapter.java
├── prompt/SkillPromptTemplates.java
├── service/IAgentSkillWorkflowService.java
└── service/impl/
    ├── AgentSkillWorkflowServiceImpl.java
    └── SkillWorkflowEngine.java
```

---

### Task 1: Skill 模型与注册表

**Files:**
- Create: `skill/model/*.java`、`SkillRegistry.java`、`SkillDefinitions.java`

- [x] 创建 `SkillStep`（tool, retry, onFailure）
- [x] 创建 `SkillDefinition`（name, description, version, steps）
- [x] 创建 `SkillSelectionResult`（skill, reason, confidence）
- [x] 创建 `SkillWorkflowContext`（跨步状态 + progress 列表）
- [x] 创建 `SkillDefinitions` 内置 `nlp2sql-query`（9 tool）与 `chitchat`（空 steps）
- [x] 创建 `SkillRegistry`：启动注册、listForAgent、require(name)

---

### Task 2: SkillSelectorAgent

**Files:**
- Create: `prompt/SkillPromptTemplates.java`
- Create: `skill/ISkillSelectorAgent.java`、`skill/impl/SkillSelectorAgent.java`

- [x] Prompt：只输出 JSON `{skill,reason,confidence}`，只能从 available_skills 选
- [x] 实现 LLM 调用 + JSON 解析；未知 skill 抛业务异常或回落校验由上层处理

---

### Task 3: SkillToolAdapter

**Files:**
- Create: `skill/adapter/SkillToolAdapter.java`

- [x] 注入 IntentTool / RetrievalTool / CandidateContextTool / DslGenerationTool / ValidationTool / EnrichmentTool / TranslationTool / ReviewTool / SqlExecutionTool / IAnswerPipelineService
- [x] `execute(toolName, ctx)` switch 绑定 9 个 tool；未知 tool 抛异常
- [x] `classify_intent` 若 NON_BUSINESS：设置 ctx 标记供引擎短路
- [x] `answer`：调用 AnswerPipeline 并把 Flux 暂存到 ctx（或返回 summary，由引擎拼流）

---

### Task 4: SkillWorkflowEngine + Service + Controller

**Files:**
- Create: `service/IAgentSkillWorkflowService.java`
- Create: `service/impl/SkillWorkflowEngine.java`
- Create: `service/impl/AgentSkillWorkflowServiceImpl.java`
- Create: `controller/Nlp2Dsl2SqlAgentSkillWorkflowController.java`

- [x] Engine：按 steps 执行，推送 progress，支持 retry，失败 ABORT
- [x] Service：空问题校验 → select skill → chitchat 短路简答 → else engine
- [x] Controller：`GET /nlp2Dsl2SqlAgentSkillWorkflow`，入参 `Nlp2DslAgentRequest`，出参 `Flux<String>`
- [x] 编译验证

---

### Task 5: 文档状态

- [x] 设计文档状态改为「已批准」
- [x] （可选）前端切换项不在首版强制；若改 HTML 需另开任务
