# Graph Report - nlp2dsl2sql  (2026-09-16)

## Corpus Check
- 154 files · ~156,819 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 98 file(s) not represented in the graph (top: .jar 52, .license 22, .xml 12)

## Summary
- 1099 nodes · 2855 edges · 56 communities (40 shown, 16 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 232 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4e7dcfe2`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- lombok.extern.slf4j.Slf4j
- A2aSqlHitlRunner
- io.agentscope.harness.agent.HarnessAgent
- reactor.core.publisher.Flux
- DslMetricDimension
- A2aHostModelRouter
- HostTraceRecorder
- SqlDialect
- EnrichedQueryDSL
- lombok.Data
- org.apache.ibatis.annotations.Mapper
- StepType
- DslMetaDataServiceImpl
- .waitDecision
- DslSynonymMapper
- 3.2 工具函数设计（Supervisor 的 Toolkit）
- A2aHostServiceImpl.java
- DslAttributeMapper
- DslEntityMapper
- HostTraceRecord
- DslFilter
- DslEmbeddingSeedService
- AgentScope A2A Host 复合协作设计
- Agent-Skill-Workflow 分层设计
- A2aHostTraceProperties
- IntentType
- Nlp2dsl2sqlApplication
- mvn
- A2aHostPrompt
- mvnDebug
- mvnyjp
- org.example:Nlp2dsl2sql
- Planner-Workflow-Service 分层设计
- QueryWorkflowEngine
- A2A Host SQL 执行前 HITL 确认设计
- io.agentscope.core.message.Msg
- 规则优先业务意图识别设计
- AgentSessionContext
- AgentToolRegistry
- JsonlTraceMiddleware
- File Structure
- Nlp2dsl2sql Agent Rules
- File Structure
- A2A Host 前端模式切换设计
- A2aRemoteAgentTools
- Override
- File Map
- File Structure
- File Structure
- DslMetricAttribute
- Nlp2dsl2sqlAgentServiceImpl
- Harness SkillToolGroup 改造计划
- CorsConfig.java
- A2A Host 前端模式切换实现计划
- NLP2SQL 业务查询
- chitchat/SKILL.md

## God Nodes (most connected - your core abstractions)
1. `DslCandidate` - 51 edges
2. `SemanticQueryDSL` - 49 edges
3. `EnrichedQueryDSL` - 42 edges
4. `QueryWorkflowEngine` - 36 edges
5. `DslMetaDataServiceImpl` - 35 edges
6. `HostTraceRecorder` - 33 edges
7. `IDslMetaDataService` - 33 edges
8. `AgentToolRegistry` - 31 edges
9. `DslMetric` - 27 edges
10. `SemanticDslAgentServiceImpl` - 27 edges

## Surprising Connections (you probably didn't know these)
- `DslEmbeddingSeedService` --references--> `EmbeddingClient`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/semanticdsl/seed/DslEmbeddingSeedService.java → src/main/java/org/example/nlp2dsl2sql/config/EmbeddingClient.java
- `AgentToolRegistry` --references--> `CandidateContextTool`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/tools/AgentToolRegistry.java → src/main/java/org/example/nlp2dsl2sql/tools/CandidateContextTool.java
- `AgentToolRegistry` --references--> `DslGenerationTool`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/tools/AgentToolRegistry.java → src/main/java/org/example/nlp2dsl2sql/tools/DslGenerationTool.java
- `AgentToolRegistry` --references--> `EnrichmentTool`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/tools/AgentToolRegistry.java → src/main/java/org/example/nlp2dsl2sql/tools/EnrichmentTool.java
- `AgentToolRegistry` --references--> `IntentTool`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/tools/AgentToolRegistry.java → src/main/java/org/example/nlp2dsl2sql/tools/IntentTool.java

## Import Cycles
- None detected.

## Communities (56 total, 16 thin omitted)

### Community 0 - "lombok.extern.slf4j.Slf4j"
Cohesion: 0.05
Nodes (45): io.agentscope.core.model.ChatResponse, io.agentscope.core.model.GenerateOptions, io.agentscope.extensions.model.openai.OpenAIChatModel, lombok.extern.slf4j.Slf4j, lombok.RequiredArgsConstructor, org.springframework.jdbc.core.JdbcTemplate, org.springframework.stereotype.Component, org.springframework.stereotype.Service (+37 more)

### Community 1 - "A2aSqlHitlRunner"
Cohesion: 0.17
Nodes (10): io.agentscope.core.agent.RuntimeContext, io.agentscope.core.event.AgentEvent, io.agentscope.core.event.RequireUserConfirmEvent, io.agentscope.core.message.ToolResultState, io.agentscope.core.message.ToolUseBlock, io.agentscope.core.ReActAgent, A2aSqlHitlRunner, PendingSqlConfirm (+2 more)

### Community 2 - "io.agentscope.harness.agent.HarnessAgent"
Cohesion: 0.15
Nodes (6): io.agentscope.harness.agent.HarnessAgent, org.springframework.beans.factory.ObjectProvider, A2aHostAgentFactory, A2aHostSessionManager, SessionHolder, AgentSkillWorkflowServiceImpl

### Community 3 - "reactor.core.publisher.Flux"
Cohesion: 0.06
Nodes (24): lombok.AllArgsConstructor, lombok.NoArgsConstructor, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, reactor.core.publisher.Flux, A2aHostController (+16 more)

### Community 5 - "A2aHostModelRouter"
Cohesion: 0.18
Nodes (7): io.agentscope.spring.boot.openai.OpenAIProperties, A2aHostModelRouter, ModelRoute, QuestionComplexityClassifier, A2aHostModelProperties, Complexity, ModelSpec

### Community 7 - "SqlDialect"
Cohesion: 0.16
Nodes (8): TranslatorConfig, fromValue(), SqlDialect, MYSQL, POSTGRESQL, SQLSERVER, Override, PostgreSqlTranslator

### Community 8 - "EnrichedQueryDSL"
Cohesion: 0.08
Nodes (19): org.junit.jupiter.api.DisplayName, org.junit.jupiter.api.Test, org.mockito.junit.jupiter.MockitoSettings, A2aSqlConfirmRegistry, EnrichedJoin, EnrichedQueryDSL, SelectColumn, WhereColumn (+11 more)

### Community 9 - "lombok.Data"
Cohesion: 0.06
Nodes (23): com.baomidou.mybatisplus.annotation.TableName, lombok.Data, org.junit.jupiter.api.extension.ExtendWith, org.mockito.junit.jupiter.MockitoExtension, DslCandidate, SemanticFilter, SemanticQueryDSL, WorkflowContext (+15 more)

### Community 10 - "org.apache.ibatis.annotations.Mapper"
Cohesion: 0.20
Nodes (3): org.apache.ibatis.annotations.Mapper, DslDimensionValueMapper, DslRelationMapper

### Community 11 - "StepType"
Cohesion: 0.10
Nodes (19): FailureAction, ABORT, REPLAN, RETRY, SKIP, StepType, ANSWER, ENRICH (+11 more)

### Community 12 - "DslMetaDataServiceImpl"
Cohesion: 0.12
Nodes (4): DslDimensionMapper, DslMetricMapper, DslMetaDataServiceImpl, Override

### Community 13 - ".waitDecision"
Cohesion: 0.29
Nodes (4): Step, Override, TimedStep, A2aSqlHitlRunnerWaitDecisionTest

### Community 15 - "3.2 工具函数设计（Supervisor 的 Toolkit）"
Cohesion: 0.06
Nodes (34): 1.1 当前 Workflow 实现特点, 1.2 HarnessAgent 已验证的能力, 2.1 架构总览, 2.2 Agent 角色定义, 2.3 分类依据：HarnessAgent vs 工具函数, 3.1 Supervisor Agent（编排者）, 3.2.1 `classify_intent` — 意图识别工具, 3.2.2 `retrieve_metadata` — 语义检索工具 (+26 more)

### Community 16 - "A2aHostServiceImpl.java"
Cohesion: 0.19
Nodes (4): lombok.Getter, Many, A2aHostChatContext, A2aSqlConfirmTexts

### Community 19 - "HostTraceRecord"
Cohesion: 0.29
Nodes (3): HostTraceRecord, A2aHostServiceImpl, Override

### Community 22 - "AgentScope A2A Host 复合协作设计"
Cohesion: 0.06
Nodes (31): 10. 测试验收, 11. 包结构示意, 12. 成功标准, 13. 相对早期草稿的变更, 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标（首版） (+23 more)

### Community 23 - "Agent-Skill-Workflow 分层设计"
Cohesion: 0.06
Nodes (30): 10. 开放问题（首版已拍板默认值）, 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标（首版不做）, 1. 背景与目标, 2.1 分层职责, 2.2 包结构 (+22 more)

### Community 24 - "A2aHostTraceProperties"
Cohesion: 0.26
Nodes (3): org.junit.jupiter.api.BeforeEach, org.springframework.boot.context.properties.ConfigurationProperties, A2aHostTraceProperties

### Community 25 - "IntentType"
Cohesion: 0.14
Nodes (8): Hit, RuleIntentClassifier, IntentType, DETAIL_QUERY, DIMENSION_ANALYSIS, METRIC_QUERY, NON_BUSINESS, DslGenerationTool

### Community 26 - "Nlp2dsl2sqlApplication"
Cohesion: 0.60
Nodes (3): org.mybatis.spring.annotation.MapperScan, org.springframework.boot.autoconfigure.SpringBootApplication, Nlp2dsl2sqlApplication

### Community 27 - "mvn"
Cohesion: 0.70
Nodes (4): mvn script, concat_lines(), find_file_argument_basedir(), find_maven_basedir()

### Community 32 - "Planner-Workflow-Service 分层设计"
Cohesion: 0.08
Nodes (25): 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标（首版不做）, 1. 背景与目标, 2.1 分层职责, 2.2 包结构, 2.3 调用关系 (+17 more)

### Community 33 - "QueryWorkflowEngine"
Cohesion: 0.11
Nodes (21): IntentType, org.example.nlp2dsl2sql.enums.planner.StepType, org.example.nlp2dsl2sql.models.dto.WorkflowContext, org.example.nlp2dsl2sql.models.entity.planner.PlanStep, org.example.nlp2dsl2sql.models.entity.planner.QueryPlan, org.example.nlp2dsl2sql.planner.IQueryPlanner, org.example.nlp2dsl2sql.service.IQueryWorkflowEngine, org.example.nlp2dsl2sql.service.pipeline.IAnswerPipelineService (+13 more)

### Community 34 - "A2A Host SQL 执行前 HITL 确认设计"
Cohesion: 0.09
Nodes (22): 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标（首版）, 1. 背景与目标, 2.1 示例轨迹, 2. 架构, 3.1 HITL SQL Agent Bean (+14 more)

### Community 36 - "规则优先业务意图识别设计"
Cohesion: 0.10
Nodes (19): 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标, 1. 背景与目标, 2. 流程, 3. 模块, 4.1 优先级与关键词（示意，实现时可微调同义词） (+11 more)

### Community 37 - "AgentSessionContext"
Cohesion: 0.21
Nodes (3): io.agentscope.core.tool.Tool, AgentSessionContext, Override

### Community 38 - "AgentToolRegistry"
Cohesion: 0.24
Nodes (9): org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, SqlQueryA2aServerAgentConfig, SqlQueryHitlAgentFactory, Nlp2dsl2sqlAgent, Nlp2dsl2sqlSkillHarnessAgent, HostTraceConfig (+1 more)

### Community 39 - "JsonlTraceMiddleware"
Cohesion: 0.25
Nodes (6): com.alibaba.fastjson2.JSONObject, io.agentscope.core.agent.Agent, io.agentscope.core.middleware.AgentInput, io.agentscope.core.middleware.MiddlewareBase, Override, JsonlTraceMiddleware

### Community 40 - "File Structure"
Cohesion: 0.14
Nodes (13): AgentScope A2A Host Implementation Plan, File Structure, Placeholder Scan, Spec Coverage Checklist, Task 1: 依赖与配置骨架, Task 2: Msg 文本提取工具（可单测）, Task 3: A2aAgent Bean + Remote Tools, Task 4: SQL A2A Server 用 ReActAgent (+5 more)

### Community 41 - "Nlp2dsl2sql Agent Rules"
Cohesion: 0.17
Nodes (11): API, Architecture, Backend Rules, Commands, Config And Data, More Rules, Never Do, Nlp2dsl2sql Agent Rules (+3 more)

### Community 42 - "File Structure"
Cohesion: 0.17
Nodes (11): A2A Host SQL HITL 确认 Implementation Plan, Execution handoff, File Structure, Spec coverage checklist, Task 1: 确认文本工具 + 单测, Task 2: VO / 挂起模型 / Registry, Task 3: HITL Agent Factory, Task 4: 改造 `call_sql_agent` 为本地 HITL (+3 more)

### Community 43 - "A2A Host 前端模式切换设计"
Cohesion: 0.17
Nodes (11): 1. Header 按钮, 2. `switchMode(mode)`, 3. `getApiEndpoint()`, 4. 请求与流式解析, A2A Host 前端模式切换设计, 变更点, 成功标准, 方案 (+3 more)

### Community 44 - "A2aRemoteAgentTools"
Cohesion: 0.29
Nodes (6): io.agentscope.core.a2a.agent.A2aAgent, io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver, A2aClientConfig, A2aRemoteAgentTools, A2aClientProperties, AgentEndpoint

### Community 46 - "File Map"
Cohesion: 0.22
Nodes (8): File Map, Global Constraints, Planner-Workflow-Service Implementation Plan, Task 1: Models, Task 2: Pipeline Services, Task 3: Planner, Task 4: Workflow + Controller, Task 5: Compile

### Community 47 - "File Structure"
Cohesion: 0.25
Nodes (7): Agent-Skill-Workflow Implementation Plan, File Structure, Task 1: Skill 模型与注册表, Task 2: SkillSelectorAgent, Task 3: SkillToolAdapter, Task 4: SkillWorkflowEngine + Service + Controller, Task 5: 文档状态

### Community 48 - "File Structure"
Cohesion: 0.25
Nodes (7): File Structure, Placeholder Scan, Rule-First Intent Classification Implementation Plan, Spec Coverage, Task 1: RuleIntentClassifier（TDD）, Task 2: IntentTool 规则优先, Task 3: V2 委托 IntentTool

### Community 50 - "Nlp2dsl2sqlAgentServiceImpl"
Cohesion: 0.33
Nodes (3): AgentEvent, Override, Nlp2dsl2sqlAgentServiceImpl

### Community 51 - "Harness SkillToolGroup 改造计划"
Cohesion: 0.33
Nodes (5): Harness SkillToolGroup 改造计划, Task 1: SKILL.md + Agent Bean, Task 2: Service 改用 Harness + RuntimeContext, Task 3: Request/前端传 userId、sessionId, Task 4: 编译验证

### Community 52 - "CorsConfig.java"
Cohesion: 0.47
Nodes (4): org.springframework.web.servlet.config.annotation.CorsRegistry, org.springframework.web.servlet.config.annotation.WebMvcConfigurer, CorsConfig, Override

### Community 54 - "A2A Host 前端模式切换实现计划"
Cohesion: 0.40
Nodes (4): A2A Host 前端模式切换实现计划, 任务 1：增加 A2A Host 模式入口, 文件结构, 自检

## Knowledge Gaps
- **213 isolated node(s):** `CONTINUE`, `NEED_REPLAN`, `COMPLETED`, `ABORT`, `REPLAN` (+208 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 281 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DslCandidate` connect `lombok.Data` to `lombok.extern.slf4j.Slf4j`, `QueryWorkflowEngine`, `reactor.core.publisher.Flux`, `AgentSessionContext`, `DslFilter`, `IntentType`?**
  _High betweenness centrality (0.051) - this node is a cross-community bridge._
- **Why does `HostTraceRecorder` connect `HostTraceRecorder` to `lombok.extern.slf4j.Slf4j`, `A2aSqlHitlRunner`, `io.agentscope.harness.agent.HarnessAgent`, `A2aRemoteAgentTools`, `.waitDecision`, `A2aHostServiceImpl.java`, `HostTraceRecord`, `A2aHostTraceProperties`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `EnrichedQueryDSL` connect `EnrichedQueryDSL` to `lombok.extern.slf4j.Slf4j`, `reactor.core.publisher.Flux`, `AgentSessionContext`, `SqlDialect`, `lombok.Data`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **What connects `CONTINUE`, `NEED_REPLAN`, `COMPLETED` to the rest of the system?**
  _213 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `lombok.extern.slf4j.Slf4j` be split into smaller, more focused modules?**
  _Cohesion score 0.05110602593440122 - nodes in this community are weakly interconnected._
- **Should `reactor.core.publisher.Flux` be split into smaller, more focused modules?**
  _Cohesion score 0.06070175438596491 - nodes in this community are weakly interconnected._
- **Should `EnrichedQueryDSL` be split into smaller, more focused modules?**
  _Cohesion score 0.07502131287297528 - nodes in this community are weakly interconnected._