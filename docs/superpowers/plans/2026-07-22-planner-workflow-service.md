# Planner-Workflow-Service Implementation Plan

> **For agentic workers:** Inline execution in this session (user requested immediate implement).

**Goal:** 新增 `/aiChat/nlp2Dsl2SqlPlannerWorkflow`，按 Planner → Workflow → Pipeline Service 分层实现。

**Architecture:** LLM Planner 产出 `QueryPlan`；`QueryWorkflowEngine` 校验并按 `StepType` 调度独立 Pipeline Service；失败可重规划（最多 maxReplan 次）。

**Tech Stack:** Java 21、Spring Boot 3.x、AgentScope OpenAIChatModel、现有 semanticdsl 组件。

## Global Constraints

- 不改现有 ReAct / V2 接口
- Controller 入参 `Nlp2DslAgentRequest`，出参 `Flux<String>`
- 方法加注释；单行 ≤100 字符；单方法 ≤300 行
- 遵循仓库 Java 21 / Boot 3（不降级）
- 无独立测试套件时以 `mvnw compile` 验证

## File Map

| 文件 | 职责 |
|------|------|
| `planner/model/*` | StepType、FailureAction、PlanGoal、PlanStep、QueryPlan |
| `planner/IQueryPlanner` + `QueryPlanner` | plan / replan |
| `workflow/*` | Context、Exception、Engine |
| `service/pipeline/*` | 8 个 Pipeline Service |
| `controller/Nlp2Dsl2SqlPlannerWorkflowController` | 新 SSE 接口 |

### Task 1: Models
- [x] 创建枚举与计划模型

### Task 2: Pipeline Services
- [x] 8 个接口 + Impl（注入复用底层组件）

### Task 3: Planner
- [x] IQueryPlanner + QueryPlanner + prompt 常量

### Task 4: Workflow + Controller
- [x] Engine 调度/门禁/重规划 + Controller

### Task 5: Compile
- [x] `mvnw -DskipTests compile`
