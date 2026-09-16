# Graph Report - nlp2dsl2sql  (2026-09-16)

## Corpus Check
- 154 files · ~156,875 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 98 file(s) not represented in the graph (top: .jar 52, .license 22, .xml 12)

## Summary
- 1084 nodes · 2865 edges · 57 communities (43 shown, 14 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 232 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `6c671b1e`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- lombok.extern.slf4j.Slf4j
- HostTraceRecorder
- io.agentscope.harness.agent.HarnessAgent
- reactor.core.publisher.Flux
- SemanticQueryDSL
- io.agentscope.extensions.model.openai.OpenAIChatModel
- SemanticDslAgentServiceImpl
- DslCandidate
- EnrichedQueryDSL
- IDslMetaDataService
- org.apache.ibatis.annotations.Mapper
- QueryWorkflowEngine
- DslMetaDataServiceImpl
- SqlExecuteTool
- DslSynonym
- 3.2 工具函数设计（Supervisor 的 Toolkit）
- lombok.Data
- DslAttribute
- DslEntityMapper
- DslGeneratePipelineServiceImpl
- DslFilter
- DslEmbeddingSeedService
- AgentScope A2A Host 复合协作设计
- Agent-Skill-Workflow 分层设计
- org.junit.jupiter.api.DisplayName
- IntentType
- Nlp2dsl2sqlApplication
- mvn
- A2aHostPrompt
- mvnDebug
- mvnyjp
- org.example:Nlp2dsl2sql
- Planner-Workflow-Service 分层设计
- QueryWorkflowEngine.java
- A2A Host SQL 执行前 HITL 确认设计
- IntentResult
- 规则优先业务意图识别设计
- AgentSessionContext
- org.springframework.context.annotation.Configuration
- JsonlTraceMiddleware
- File Structure
- Nlp2dsl2sql Agent Rules
- File Structure
- A2A Host 前端模式切换设计
- A2aRemoteAgentTools
- A2aHostModelProperties
- File Map
- File Structure
- File Structure
- DslMetricAttribute
- Nlp2dsl2sqlAgentServiceImpl
- Harness SkillToolGroup 改造计划
- CorsConfig.java
- ReviewTool
- A2A Host 前端模式切换实现计划
- NLP2SQL 业务查询
- chitchat/SKILL.md

## God Nodes (most connected - your core abstractions)
1. `DslCandidate` - 52 edges
2. `SemanticQueryDSL` - 50 edges
3. `EnrichedQueryDSL` - 43 edges
4. `QueryWorkflowEngine` - 37 edges
5. `DslMetaDataServiceImpl` - 35 edges
6. `HostTraceRecorder` - 33 edges
7. `IDslMetaDataService` - 33 edges
8. `AgentToolRegistry` - 31 edges
9. `IntentType` - 27 edges
10. `DslMetric` - 27 edges

## Surprising Connections (you probably didn't know these)
- `A2aHostAgentFactory` --references--> `A2aRemoteAgentTools`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostAgentFactory.java → src/main/java/org/example/nlp2dsl2sql/a2a/A2aRemoteAgentTools.java
- `A2aHostAgentFactory` --references--> `JsonlTraceMiddleware`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostAgentFactory.java → src/main/java/org/example/nlp2dsl2sql/a2a/trace/JsonlTraceMiddleware.java
- `A2aHostModelRouter` --references--> `QuestionComplexityClassifier`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostModelRouter.java → src/main/java/org/example/nlp2dsl2sql/a2a/QuestionComplexityClassifier.java
- `A2aHostModelRouter` --references--> `A2aHostModelProperties`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostModelRouter.java → src/main/java/org/example/nlp2dsl2sql/config/A2aHostModelProperties.java
- `A2aHostServiceImpl` --references--> `A2aHostModelRouter`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/service/impl/A2aHostServiceImpl.java → src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostModelRouter.java

## Import Cycles
- None detected.

## Communities (57 total, 14 thin omitted)

### Community 0 - "lombok.extern.slf4j.Slf4j"
Cohesion: 0.14
Nodes (16): lombok.extern.slf4j.Slf4j, lombok.RequiredArgsConstructor, org.springframework.stereotype.Component, org.springframework.web.client.RestClient, Nlp2dsl2sqlSkillHarnessAgent, EmbeddingClient, RerankClient, EnrichmentPipelineServiceImpl (+8 more)

### Community 1 - "HostTraceRecorder"
Cohesion: 0.05
Nodes (27): io.agentscope.core.agent.RuntimeContext, io.agentscope.core.event.AgentEvent, io.agentscope.core.event.RequireUserConfirmEvent, io.agentscope.core.message.Msg, io.agentscope.core.message.ToolResultState, io.agentscope.core.message.ToolUseBlock, io.agentscope.core.ReActAgent, lombok.Getter (+19 more)

### Community 2 - "io.agentscope.harness.agent.HarnessAgent"
Cohesion: 0.19
Nodes (5): io.agentscope.harness.agent.HarnessAgent, org.springframework.beans.factory.ObjectProvider, A2aHostAgentFactory, A2aHostSessionManager, SessionHolder

### Community 3 - "reactor.core.publisher.Flux"
Cohesion: 0.07
Nodes (23): lombok.AllArgsConstructor, lombok.NoArgsConstructor, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, reactor.core.publisher.Flux, A2aHostController (+15 more)

### Community 4 - "SemanticQueryDSL"
Cohesion: 0.18
Nodes (6): org.mockito.junit.jupiter.MockitoSettings, SemanticQueryDSL, SemanticDslValidator, ValidationResult, Override, SemanticDslValidatorTest

### Community 5 - "io.agentscope.extensions.model.openai.OpenAIChatModel"
Cohesion: 0.31
Nodes (5): io.agentscope.extensions.model.openai.OpenAIChatModel, io.agentscope.spring.boot.openai.OpenAIProperties, A2aHostModelRouter, ModelRoute, ModelSpec

### Community 7 - "DslCandidate"
Cohesion: 0.19
Nodes (5): org.junit.jupiter.api.extension.ExtendWith, DslCandidate, DslEntity, SemanticDslEnricher, SemanticDslEnricherTest

### Community 8 - "EnrichedQueryDSL"
Cohesion: 0.07
Nodes (19): fromValue(), SqlDialect, MYSQL, POSTGRESQL, SQLSERVER, EnrichedJoin, EnrichedQueryDSL, SelectColumn (+11 more)

### Community 9 - "IDslMetaDataService"
Cohesion: 0.11
Nodes (4): IDslMetaDataService, Override, DslRetriever, SuppressWarnings

### Community 10 - "org.apache.ibatis.annotations.Mapper"
Cohesion: 0.16
Nodes (4): org.apache.ibatis.annotations.Mapper, DslDimensionValueMapper, DslMetricDimensionMapper, DslRelationMapper

### Community 11 - "QueryWorkflowEngine"
Cohesion: 0.06
Nodes (28): FailureAction, ABORT, REPLAN, RETRY, SKIP, StepType, ANSWER, ENRICH (+20 more)

### Community 12 - "DslMetaDataServiceImpl"
Cohesion: 0.10
Nodes (4): DslDimensionMapper, DslMetricMapper, DslMetaDataServiceImpl, Override

### Community 13 - "SqlExecuteTool"
Cohesion: 0.25
Nodes (5): org.springframework.jdbc.core.JdbcTemplate, Override, SqlExecutePipelineServiceImpl, ISqlExecutePipelineService, SqlExecuteTool

### Community 15 - "3.2 工具函数设计（Supervisor 的 Toolkit）"
Cohesion: 0.06
Nodes (34): 1.1 当前 Workflow 实现特点, 1.2 HarnessAgent 已验证的能力, 2.1 架构总览, 2.2 Agent 角色定义, 2.3 分类依据：HarnessAgent vs 工具函数, 3.1 Supervisor Agent（编排者）, 3.2.1 `classify_intent` — 意图识别工具, 3.2.2 `retrieve_metadata` — 语义检索工具 (+26 more)

### Community 16 - "lombok.Data"
Cohesion: 0.26
Nodes (8): com.baomidou.mybatisplus.annotation.TableName, lombok.Data, SemanticFilter, DslDimension, DslDimensionValue, DslMetric, DslMetricDimension, DslRelation

### Community 19 - "DslGeneratePipelineServiceImpl"
Cohesion: 0.27
Nodes (4): PlanGoal, IDslGeneratePipelineService, DslGeneratePipelineServiceImpl, Override

### Community 22 - "AgentScope A2A Host 复合协作设计"
Cohesion: 0.06
Nodes (31): 10. 测试验收, 11. 包结构示意, 12. 成功标准, 13. 相对早期草稿的变更, 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标（首版） (+23 more)

### Community 23 - "Agent-Skill-Workflow 分层设计"
Cohesion: 0.06
Nodes (30): 10. 开放问题（首版已拍板默认值）, 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标（首版不做）, 1. 背景与目标, 2.1 分层职责, 2.2 包结构 (+22 more)

### Community 24 - "org.junit.jupiter.api.DisplayName"
Cohesion: 0.18
Nodes (7): org.junit.jupiter.api.BeforeEach, org.junit.jupiter.api.DisplayName, org.junit.jupiter.api.Test, org.mockito.junit.jupiter.MockitoExtension, A2aSqlConfirmRegistry, A2aHostTraceProperties, A2aSqlConfirmRegistryTest

### Community 25 - "IntentType"
Cohesion: 0.16
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

### Community 33 - "QueryWorkflowEngine.java"
Cohesion: 0.14
Nodes (11): org.springframework.stereotype.Service, Nlp2dsl2sqlException, ReviewResult, DslTranslator, Override, ReviewPipelineServiceImpl, TranslationPipelineServiceImpl, ValidationPipelineServiceImpl (+3 more)

### Community 34 - "A2A Host SQL 执行前 HITL 确认设计"
Cohesion: 0.09
Nodes (22): 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标（首版）, 1. 背景与目标, 2.1 示例轨迹, 2. 架构, 3.1 HITL SQL Agent Bean (+14 more)

### Community 35 - "IntentResult"
Cohesion: 0.20
Nodes (6): io.agentscope.core.model.ChatResponse, io.agentscope.core.model.GenerateOptions, IntentResult, SemanticPromptTemplates, Override, IntentTool

### Community 36 - "规则优先业务意图识别设计"
Cohesion: 0.10
Nodes (19): 1.1 现状, 1.2 目标, 1.3 已确认决策, 1.4 非目标, 1. 背景与目标, 2. 流程, 3. 模块, 4.1 优先级与关键词（示意，实现时可微调同义词） (+11 more)

### Community 37 - "AgentSessionContext"
Cohesion: 0.18
Nodes (3): io.agentscope.core.tool.Tool, AgentSessionContext, Override

### Community 38 - "org.springframework.context.annotation.Configuration"
Cohesion: 0.25
Nodes (7): org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, SqlQueryA2aServerAgentConfig, Nlp2dsl2sqlAgent, HostTraceConfig, TranslatorConfig

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
Cohesion: 0.36
Nodes (6): io.agentscope.core.a2a.agent.A2aAgent, io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver, A2aClientConfig, A2aRemoteAgentTools, A2aClientProperties, AgentEndpoint

### Community 45 - "A2aHostModelProperties"
Cohesion: 0.27
Nodes (4): org.springframework.boot.context.properties.ConfigurationProperties, QuestionComplexityClassifier, A2aHostModelProperties, Complexity

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
- **213 isolated node(s):** `org.example:Nlp2dsl2sql`, `POSTGRESQL`, `MYSQL`, `SQLSERVER`, `RETRY` (+208 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 281 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `EnrichedQueryDSL` connect `EnrichedQueryDSL` to `lombok.extern.slf4j.Slf4j`, `QueryWorkflowEngine.java`, `IntentResult`, `AgentSessionContext`, `SemanticDslAgentServiceImpl`, `DslCandidate`, `QueryWorkflowEngine`, `lombok.Data`, `ReviewTool`, `org.junit.jupiter.api.DisplayName`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Why does `QueryWorkflowEngine` connect `QueryWorkflowEngine` to `lombok.extern.slf4j.Slf4j`, `QueryWorkflowEngine.java`, `reactor.core.publisher.Flux`, `EnrichedQueryDSL`, `SqlExecuteTool`, `DslGeneratePipelineServiceImpl`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **Why does `IDslMetaDataService` connect `IDslMetaDataService` to `lombok.extern.slf4j.Slf4j`, `SemanticQueryDSL`, `DslCandidate`, `DslMetaDataServiceImpl`, `DslSynonym`, `lombok.Data`, `DslMetricAttribute`?**
  _High betweenness centrality (0.036) - this node is a cross-community bridge._
- **What connects `org.example:Nlp2dsl2sql`, `POSTGRESQL`, `MYSQL` to the rest of the system?**
  _213 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `lombok.extern.slf4j.Slf4j` be split into smaller, more focused modules?**
  _Cohesion score 0.1417004048582996 - nodes in this community are weakly interconnected._
- **Should `HostTraceRecorder` be split into smaller, more focused modules?**
  _Cohesion score 0.05078416728902166 - nodes in this community are weakly interconnected._
- **Should `reactor.core.publisher.Flux` be split into smaller, more focused modules?**
  _Cohesion score 0.07341269841269842 - nodes in this community are weakly interconnected._