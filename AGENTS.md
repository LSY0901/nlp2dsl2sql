# AGENTS.md

> 本文件为 AI 编程助手（Agent）提供项目上下文，帮助快速理解代码库结构、架构与约定。

## 项目概述

**Nlp2dsl2sql** 是一个基于 Spring Boot + AgentScope 的企业级自然语言数据查询系统。它通过**语义层管线（Semantic Layer Pipeline）**将用户自然语言问题转换为结构化 DSL，再翻译为安全的 SQL 并执行，最终以自然语言返回查询结论。

核心价值：用语义层（DSL 元数据 + 向量检索）解耦业务语义与物理 SQL，避免 LLM 直接生成 SQL 带来的幻觉与注入风险。

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.5.15 |
| AI Agent | AgentScope（harness + openai 扩展 + spring-boot-starter） | 2.0.0 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | PostgreSQL + pgvector | - |
| 向量检索 | pgvector（1024 维 BGE-M3） | 0.1.4 |
| 流式响应 | Spring WebFlux（SSE） | - |
| 工具库 | Lombok / FastJSON | - |
| 构建 | Maven | - |

## 架构与核心管线

核心入口：`SemanticDslAgentServiceImpl#nlp2Dsl2SqlAgentV2`，采用 **7 阶段流式管线**：

```
用户问题
  │
  ▼
[Stage 1] 意图识别        ── LLM 分类：METRIC_QUERY / DIMENSION_ANALYSIS / DETAIL_QUERY / NON_BUSINESS
  │
  ▼
[Stage 2] 语义检索        ── BGE-M3 向量召回 + 同义词扩展 + BGE-Reranker 精排
  │
  ▼
[Stage 3] 语义 DSL 生成   ── LLM 基于候选元数据生成 SemanticQueryDSL（metric/entity/dimensions/filters）
  │
  ▼
[Stage 4] DSL 校验        ── 校验 metric/entity/dimension/filter 合法性与兼容性
  │
  ▼
[Stage 5] DSL 富化        ── 将语义 code 转为物理表/列，BFS 求解 JOIN 路径，注入系统过滤
  │
  ▼
[Stage 6] SQL 生成        ── AbstractDslTranslator 将 EnrichedQueryDSL 翻译为参数化 SQL
  │
  ▼
[Stage 7] SQL 审查 + 执行  ── LLM 审查 SQL 正确性 → SqlExecuteTool 安全执行（仅 SELECT）
  │
  ▼
自然语言回答（SSE 流式）
```

## 目录结构

```
src/main/java/org/example/nlp2dsl2sql/
├── Nlp2dsl2sqlApplication.java        # 启动类（@MapperScan）
├── agent/
│   └── Nlp2dslAgent.java              # AgentScope HarnessAgent Bean 配置
├── config/
│   ├── CorsConfig.java                # 跨域配置
│   └── EmbeddingClient.java           # BGE-M3 Embedding 客户端（RestClient）
├── controller/
│   ├── AgentController.java            # /agent/chat 基础 Agent 对话
│   └── Nlp2Dsl2SqlAgentController.java# /aiChat/nlp2Dsl2SqlAgentV2 SSE 流式管线
├── mapper/dsl/                        # MyBatis-Plus Mapper（10 个 DSL 元数据表）
├── models/
│   ├── entity/ReviewResult.java       # SQL 审查结果
│   └── request/Nlp2DslAgentRequest.java
├── semanticdsl/                       # ★ 语义层核心模块
│   ├── agent/
│   │   ├── ISemanticDslAgentService.java
│   │   └── SemanticDslAgentServiceImpl.java   # 管线编排器
│   ├── enricher/
│   │   └── SemanticDslEnricher.java           # DSL 富化（物理映射 + JOIN 路径 BFS）
│   ├── metadata/
│   │   ├── IDslMetaDataService.java           # 元数据服务接口
│   │   ├── DslMetaDataServiceImpl.java        # 元数据服务实现
│   │   └── entity/                             # 10 个 DSL 实体类
│   ├── model/
│   │   ├── SemanticQueryDSL.java              # 语义层 DSL（metric/entity/dimensions/filters）
│   │   ├── EnrichedQueryDSL.java              # 富化后 DSL（物理表/列/JOIN/WHERE）
│   │   ├── DslCandidate.java                  # 检索候选集
│   │   ├── IntentResult.java                  # 意图识别结果
│   │   └── SemanticFilter.java
│   ├── prompt/
│   │   └── SemanticPromptTemplates.java       # 所有 LLM 提示词模板
│   ├── retriever/
│   │   └── DslRetriever.java                  # 向量召回 + 同义词扩展 + Rerank
│   ├── seed/
│   │   └── DslEmbeddingSeedService.java       # 启动时自动补全 embedding
│   ├── translator/
│   │   ├── DslTranslator.java                 # 翻译器接口
│   │   ├── AbstractDslTranslator.java         # 抽象翻译器（SELECT/JOIN/WHERE/GROUP BY/LIMIT）
│   │   ├── PostgreSqlTranslator.java          # PostgreSQL 方言实现
│   │   ├── SqlDialect.java                    # 方言枚举
│   │   └── TranslatorConfig.java              # 方言配置（dsl.translator.dialect）
│   └── validator/
│       └── SemanticDslValidator.java          # DSL 合法性校验
└── tools/
    ├── ReviewTool.java                        # LLM SQL 审查
    └── SqlExecuteTool.java                    # 安全 SQL 执行（SELECT only + 关键字黑名单）

src/main/resources/
├── application.yaml                          # 应用配置
├── mapper/dsl/                               # MyBatis XML（10 个）
└── static/nlp2dsl2sqlV2.html                 # 前端对话页面

ai_agent.sql                                   # 数据库建表脚本（agent schema）
```

## 数据库模型（DSL 元数据）

PostgreSQL `agent` schema，共 10 张表，带 `embedding vector(1024)` 的表支持向量检索：

| 表名 | 用途 | 向量检索 |
|------|------|:--------:|
| `dsl_entity` | 业务实体（对应物理表） | ✅ |
| `dsl_attribute` | 实体属性（对应物理列） | ✅ |
| `dsl_relation` | 实体关系（JOIN 规则） | - |
| `dsl_metric` | 业务指标（聚合表达式） | ✅ |
| `dsl_metric_attribute` | 指标字段依赖 | - |
| `dsl_dimension` | 查询维度（分组） | ✅ |
| `dsl_dimension_value` | 维度枚举值 | ✅ |
| `dsl_metric_dimension` | 指标-维度约束 | - |
| `dsl_filter` | 业务过滤规则 | - |
| `dsl_synonym` | 业务同义词（Query Rewrite） | ✅ |

## 外部服务依赖

| 服务 | 地址 | 用途 |
|------|------|------|
| LLM（DeepSeek） | `https://api.deepseek.com` | 意图识别、DSL 生成、SQL 审查、自然语言回答 |
| Embedding | `http://localhost:8082` | BGE-M3 文本向量化（1024 维） |
| Rerank | `http://localhost:8083` | BGE-reranker-v2-m3 相关性重排 |
| PostgreSQL | `localhost:5432/agent_db` | 元数据 + 业务数据（schema: `agent`） |

> LLM 配置在 `application.yaml` 的 `agentscope.openai` 节点；API Key 需手动填入。

## 关键设计要点

1. **语义层解耦**：LLM 只生成语义 DSL（code 级别），物理 SQL 由确定性代码翻译，避免幻觉。
2. **RAG 元数据检索**：向量召回 + Rerank 两阶段，Top-K 候选送入 LLM 上下文。
3. **JOIN 路径求解**：`SemanticDslEnricher` 用 BFS 在 `dsl_relation` 图上找最短 JOIN 路径，自动补全桥接实体。
4. **同义词扩展**：`dsl_synonym` 命中后扩展候选指标/维度/实体，支持 Query Rewrite。
5. **SQL 安全**：`SqlExecuteTool` 强制 SELECT-only + 关键字黑名单 + 参数绑定；`ReviewTool` 用 LLM 二次审查。
6. **Embedding 自举**：`DslEmbeddingSeedService` 在 `@PostConstruct` 时为 `embedding IS NULL` 的记录自动生成向量。
7. **流式输出**：管线执行完成后，最终自然语言回答通过 `Flux<String>` SSE 流式推送。

## 构建与运行

```bash
# 编译
./mvnw clean compile

# 打包
./mvnw clean package -DskipTests

# 运行（需先启动 PostgreSQL、Embedding、Rerank 服务，并填入 LLM API Key）
java -jar target/Nlp2dsl2sql-0.0.1-SNAPSHOT.jar
```

服务启动后访问：`http://localhost:8079/nlp2dsl2sqlV2.html`

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/agent/chat?question=xxx` | GET | 基础 Agent 对话（AgentScope HarnessAgent） |
| `/aiChat/nlp2Dsl2SqlAgentV2?question=xxx` | GET (SSE) | V2 语义层管线（7 阶段流式 workflow） |
| `/aiChat/nlp2Dsl2SqlMultiAgent?question=xxx` | GET (SSE) | 多 Agent 协作（HarnessAgent + 工具函数编排） |

## 编码约定

- **包结构**：`org.example.nlp2dsl2sql`，按职责分层（agent/config/controller/mapper/models/semanticdsl/tools）。
- **依赖注入**：优先构造器注入（`@RequiredArgsConstructor` + `final` 字段）。
- **日志**：`@Slf4j` + `log.info/warn/error`，关键阶段用 `━━━` 分隔符标记。
- **异常处理**：管线内部用 `PipelineException`（V2）/ `MultiAgentException`（多 Agent）中断并返回友好错误。
- **LLM 调用**：统一通过 `OpenAIChatModel.stream()`，DeepSeek 不支持 `json_schema`，使用 `jsonObject` 格式。
- **提示词**：集中在 `SemanticPromptTemplates` 常量类管理；多 Agent 架构额外使用 `MultiAgentConfig` 中的 Agent 专用 prompt 常量。
- **实体类**：Lombok `@Data`，DSL 元数据实体位于 `semanticdsl.metadata.entity`。
- **MyBatis**：XML 映射文件在 `resources/mapper/dsl/`，接口在 `mapper/dsl/`。
- **SQL 方言**：通过 `dsl.translator.dialect` 配置，目前仅 PostgreSQL 实现完整。

## 注意事项

- `application.yaml` 中 `agentscope.openai.api-key` 为空，运行前必须填入。
- 数据库需先执行 `ai_agent.sql` 建表，并导入业务数据（如 student/score 示例数据）。
- Embedding 服务需兼容 OpenAI `/v1/embeddings` 接口格式。
- Rerank 服务需提供 `/rerank` POST 接口，返回 `{"scores": [...]}`。
- `TranslatorConfig` 中 MYSQL/SQLSERVER 方言暂复用 PostgreSQL 实现，如需支持需新增子类。

## 多 Agent 架构（Multi-Agent）

在原 V2 Workflow 基础上，新增了基于 AgentScope Harness 的多 Agent 协作架构，通过 `/aiChat/nlp2Dsl2SqlMultiAgent` 接口暴露。

### 架构对比

| 维度 | V2 Workflow | 多 Agent |
|------|-------------|---------|
| **编排方式** | Java 代码硬编码 if/else 链 | 多个专用 HarnessAgent + 工具函数 |
| **LLM 调用** | 直接 `openAIChatModel.stream()` 无上下文 | 多个 HarnessAgent，各有独立 system prompt |
| **扩展性** | 新增阶段需改编排器代码 | 新增工具函数 + 更新 prompt |
| **会话管理** | 无 | HarnessAgent Session + 记忆压缩 |

### Agent 与工具分工

| 组件 | 类型 | 职责 | 对应 Stage |
|------|------|------|------------|
| `IntentAgent` | HarnessAgent | 意图识别 | Stage 1 |
| `RetrievalTool` | 工具函数 | 语义检索（向量 + Rerank） | Stage 2 |
| `DslGeneratorAgent` | HarnessAgent | DSL 生成 | Stage 3 |
| `ValidationTool` | 工具函数 | DSL 校验 | Stage 4 |
| `EnrichmentTool` | 工具函数 | DSL 富化（BFS JOIN） | Stage 5 |
| `TranslationTool` | 工具函数 | SQL 翻译 | Stage 6 |
| `ReviewAgent` | HarnessAgent | SQL 审查 | Stage 7a |
| `SqlExecutionTool` | 工具函数 | SQL 安全执行 | Stage 7b |
| `AnswerAgent` | HarnessAgent | 自然语言回答（流式） | Stage 7c |

### 关键文件

```
src/main/java/org/example/nlp2dsl2sql/
├── agent/
│   ├── Nlp2dslAgent.java             # 基础 Agent Bean
│   └── MultiAgentConfig.java         # ★ 多 Agent 配置（4 个 HarnessAgent Bean）
├── semanticdsl/
│   ├── agent/
│   │   ├── ISemanticDslAgentService.java   # V2 Workflow 接口
│   │   ├── SemanticDslAgentServiceImpl.java # V2 Workflow 实现
│   │   ├── IMultiAgentService.java          # ★ 多 Agent 接口
│   │   └── MultiAgentServiceImpl.java       # ★ 多 Agent 编排器
│   └── tools/                               # ★ 多 Agent 工具函数
│       ├── RetrievalTool.java
│       ├── CandidateContextTool.java
│       ├── ValidationTool.java
│       ├── EnrichmentTool.java
│       ├── TranslationTool.java
│       └── SqlExecutionTool.java
├── controller/
│   ├── Nlp2Dsl2SqlAgentController.java     # V2 Workflow 控制器
│   └── MultiAgentController.java           # ★ 多 Agent 控制器

docs/
└── MULTI_AGENT_DESIGN.md                    # 多 Agent 详细设计文档
```
