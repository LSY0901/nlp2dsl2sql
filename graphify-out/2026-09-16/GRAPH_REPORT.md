# Graph Report - nlp2dsl2sql  (2026-09-16)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 824 nodes · 2620 edges · 32 communities (20 shown, 12 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 232 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `bee76e87`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- lombok.extern.slf4j.Slf4j
- HostTraceRecorder
- io.agentscope.extensions.model.openai.OpenAIChatModel
- reactor.core.publisher.Flux
- SemanticQueryDSL
- lombok.Data
- SemanticDslAgentServiceImpl
- DslCandidate
- SqlDialect
- IDslMetaDataService
- com.baomidou.mybatisplus.annotation.TableName
- StepType
- DslMetric
- QueryWorkflowEngine
- DslSynonym
- QueryPlan
- DslMetaDataServiceImpl
- DslEmbeddingSeedService.java
- DslEntity
- DslGeneratePipelineServiceImpl
- SemanticDslEnricher.java
- DslEmbeddingSeedService
- DslDimensionValue
- .executeStepWithRetry
- .executeWorkflow
- IntentType
- Nlp2dsl2sqlApplication
- mvn
- A2aHostPrompt
- mvnDebug
- mvnyjp
- org.example:Nlp2dsl2sql

## God Nodes (most connected - your core abstractions)
1. `DslCandidate` - 52 edges
2. `SemanticQueryDSL` - 50 edges
3. `EnrichedQueryDSL` - 43 edges
4. `QueryWorkflowEngine` - 36 edges
5. `DslMetaDataServiceImpl` - 35 edges
6. `HostTraceRecorder` - 33 edges
7. `IDslMetaDataService` - 33 edges
8. `AgentToolRegistry` - 31 edges
9. `DslMetric` - 27 edges
10. `IntentType` - 27 edges

## Surprising Connections (you probably didn't know these)
- `DslEmbeddingSeedService` --references--> `EmbeddingClient`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/semanticdsl/seed/DslEmbeddingSeedService.java → src/main/java/org/example/nlp2dsl2sql/config/EmbeddingClient.java
- `AgentSessionContext` --references--> `EnrichedQueryDSL`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/models/dto/dsl/AgentSessionContext.java → src/main/java/org/example/nlp2dsl2sql/models/dto/dsl/EnrichedQueryDSL.java
- `WorkflowContext` --references--> `EnrichedQueryDSL`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/models/dto/WorkflowContext.java → src/main/java/org/example/nlp2dsl2sql/models/dto/dsl/EnrichedQueryDSL.java
- `AbstractDslTranslator` --implements--> `DslTranslator`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/semanticdsl/translator/AbstractDslTranslator.java → src/main/java/org/example/nlp2dsl2sql/semanticdsl/translator/DslTranslator.java
- `SemanticDslAgentServiceImpl` --references--> `DslTranslator`  [EXTRACTED]
  src/main/java/org/example/nlp2dsl2sql/service/impl/SemanticDslAgentServiceImpl.java → src/main/java/org/example/nlp2dsl2sql/semanticdsl/translator/DslTranslator.java

## Import Cycles
- None detected.

## Communities (32 total, 12 thin omitted)

### Community 0 - "lombok.extern.slf4j.Slf4j"
Cohesion: 0.05
Nodes (44): io.agentscope.core.model.ChatResponse, io.agentscope.core.model.GenerateOptions, lombok.extern.slf4j.Slf4j, lombok.RequiredArgsConstructor, org.springframework.jdbc.core.JdbcTemplate, org.springframework.stereotype.Component, org.springframework.stereotype.Service, org.springframework.web.client.RestClient (+36 more)

### Community 1 - "HostTraceRecorder"
Cohesion: 0.06
Nodes (25): io.agentscope.core.agent.RuntimeContext, io.agentscope.core.event.AgentEvent, io.agentscope.core.event.RequireUserConfirmEvent, io.agentscope.core.message.Msg, io.agentscope.core.message.ToolResultState, io.agentscope.core.message.ToolUseBlock, io.agentscope.core.ReActAgent, lombok.Getter (+17 more)

### Community 2 - "io.agentscope.extensions.model.openai.OpenAIChatModel"
Cohesion: 0.06
Nodes (29): com.alibaba.fastjson2.JSONObject, io.agentscope.core.agent.Agent, io.agentscope.core.middleware.AgentInput, io.agentscope.core.middleware.MiddlewareBase, io.agentscope.core.tool.Tool, io.agentscope.extensions.model.openai.OpenAIChatModel, io.agentscope.harness.agent.HarnessAgent, org.springframework.beans.factory.ObjectProvider (+21 more)

### Community 3 - "reactor.core.publisher.Flux"
Cohesion: 0.06
Nodes (27): lombok.AllArgsConstructor, lombok.NoArgsConstructor, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, reactor.core.publisher.Flux, HostTraceRecord (+19 more)

### Community 4 - "SemanticQueryDSL"
Cohesion: 0.10
Nodes (15): org.junit.jupiter.api.DisplayName, org.junit.jupiter.api.extension.ExtendWith, org.junit.jupiter.api.Test, org.mockito.junit.jupiter.MockitoExtension, org.mockito.junit.jupiter.MockitoSettings, SemanticFilter, SemanticQueryDSL, Override (+7 more)

### Community 5 - "lombok.Data"
Cohesion: 0.07
Nodes (17): io.agentscope.core.a2a.agent.A2aAgent, io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver, io.agentscope.spring.boot.openai.OpenAIProperties, lombok.Data, org.junit.jupiter.api.BeforeEach, org.springframework.boot.context.properties.ConfigurationProperties, A2aClientConfig, A2aHostModelRouter (+9 more)

### Community 6 - "SemanticDslAgentServiceImpl"
Cohesion: 0.10
Nodes (7): Hit, RuleIntentClassifier, IntentResult, Override, PipelineException, SemanticDslAgentServiceImpl, IntentTool

### Community 7 - "DslCandidate"
Cohesion: 0.14
Nodes (5): DslCandidate, EnrichedJoin, DslRelation, SemanticDslEnricher, Override

### Community 8 - "SqlDialect"
Cohesion: 0.13
Nodes (11): fromValue(), SqlDialect, MYSQL, POSTGRESQL, SQLSERVER, SelectColumn, WhereColumn, AbstractDslTranslator (+3 more)

### Community 10 - "com.baomidou.mybatisplus.annotation.TableName"
Cohesion: 0.15
Nodes (7): com.baomidou.mybatisplus.annotation.TableName, org.apache.ibatis.annotations.Mapper, DslMetricAttributeMapper, DslMetricDimensionMapper, DslRelationMapper, DslMetricAttribute, DslMetricDimension

### Community 11 - "StepType"
Cohesion: 0.12
Nodes (15): FailureAction, ABORT, REPLAN, RETRY, SKIP, StepType, ANSWER, ENRICH (+7 more)

### Community 12 - "DslMetric"
Cohesion: 0.20
Nodes (3): DslMetricMapper, DslMetric, Override

### Community 15 - "QueryPlan"
Cohesion: 0.26
Nodes (4): QueryPlan, Override, QueryPlanner, IQueryPlanner

### Community 16 - "DslMetaDataServiceImpl"
Cohesion: 0.28
Nodes (3): DslDimensionMapper, DslDimension, DslMetaDataServiceImpl

### Community 17 - "DslEmbeddingSeedService.java"
Cohesion: 0.26
Nodes (3): jakarta.annotation.PostConstruct, DslAttributeMapper, DslAttribute

### Community 19 - "DslGeneratePipelineServiceImpl"
Cohesion: 0.27
Nodes (4): PlanGoal, IDslGeneratePipelineService, DslGeneratePipelineServiceImpl, Override

### Community 23 - ".executeStepWithRetry"
Cohesion: 0.39
Nodes (4): StepLoopResult, COMPLETED, CONTINUE, NEED_REPLAN

### Community 25 - "IntentType"
Cohesion: 0.33
Nodes (5): IntentType, DETAIL_QUERY, DIMENSION_ANALYSIS, METRIC_QUERY, NON_BUSINESS

### Community 26 - "Nlp2dsl2sqlApplication"
Cohesion: 0.60
Nodes (3): org.mybatis.spring.annotation.MapperScan, org.springframework.boot.autoconfigure.SpringBootApplication, Nlp2dsl2sqlApplication

### Community 27 - "mvn"
Cohesion: 0.70
Nodes (4): mvn script, concat_lines(), find_file_argument_basedir(), find_maven_basedir()

## Knowledge Gaps
- **23 isolated node(s):** `ABORT`, `REPLAN`, `RETRY`, `SKIP`, `ANSWER` (+18 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 75 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `EnrichedQueryDSL` connect `lombok.extern.slf4j.Slf4j` to `io.agentscope.extensions.model.openai.OpenAIChatModel`, `SemanticQueryDSL`, `lombok.Data`, `SemanticDslAgentServiceImpl`, `DslCandidate`, `SqlDialect`, `StepType`, `QueryWorkflowEngine`, `SemanticDslEnricher.java`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Why does `DslCandidate` connect `DslCandidate` to `lombok.extern.slf4j.Slf4j`, `io.agentscope.extensions.model.openai.OpenAIChatModel`, `SemanticQueryDSL`, `lombok.Data`, `SemanticDslAgentServiceImpl`, `IDslMetaDataService`, `StepType`, `DslMetric`, `QueryWorkflowEngine`, `DslSynonym`, `DslMetaDataServiceImpl`, `DslEmbeddingSeedService.java`, `DslEntity`, `DslGeneratePipelineServiceImpl`, `SemanticDslEnricher.java`, `DslDimensionValue`?**
  _High betweenness centrality (0.058) - this node is a cross-community bridge._
- **Why does `DslMetaDataServiceImpl` connect `DslMetaDataServiceImpl` to `lombok.extern.slf4j.Slf4j`, `DslCandidate`, `IDslMetaDataService`, `com.baomidou.mybatisplus.annotation.TableName`, `DslMetric`, `DslSynonym`, `DslEmbeddingSeedService.java`, `DslEntity`, `SemanticDslEnricher.java`, `DslDimensionValue`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **What connects `ABORT`, `REPLAN`, `RETRY` to the rest of the system?**
  _23 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `lombok.extern.slf4j.Slf4j` be split into smaller, more focused modules?**
  _Cohesion score 0.05082458770614692 - nodes in this community are weakly interconnected._
- **Should `HostTraceRecorder` be split into smaller, more focused modules?**
  _Cohesion score 0.05562714776632302 - nodes in this community are weakly interconnected._
- **Should `io.agentscope.extensions.model.openai.OpenAIChatModel` be split into smaller, more focused modules?**
  _Cohesion score 0.05555555555555555 - nodes in this community are weakly interconnected._