# Nlp2dsl2sql Agent Rules

Spring Boot 3.5.15 + Java 21 + AgentScope 2.0.0 自然语言数据查询系统。

本文件是跨工具 Agent 入口，只放长期有效、代码里不容易直接推断、猜错会影响结果的规则。多 Agent 架构演进见 `docs/MULTI_AGENT_DESIGN.md`。

## Tech Stack

- Backend: Spring Boot 3.5.15 / Java 21 / Maven / AgentScope 2.0.0（harness + openai 扩展 + a2a starter）
- Database: PostgreSQL + pgvector，向量维度 1024，BGE-M3 编码
- ORM: MyBatis-Plus 3.5.7
- JSON: fastjson2（业务代码统一使用，不要混入 Jackson）
- Streaming: WebFlux `Flux<String>` SSE 输出
- Frontend: 单页演示 `src/main/resources/static/nlp2dsl2sqlV2.html`

## Commands

```bash
mvn clean compile              # 改完代码至少跑这个（当前无测试套件）
mvn clean package -DskipTests  # 打包
java -jar target/Nlp2dsl2sql-0.0.1-SNAPSHOT.jar
```

访问：`http://localhost:8079/nlp2dsl2sqlV2.html`，默认端口 `8079`。

## Project Structure

- `semanticdsl/`: 语义层核心。`validator` DSL 校验、`enricher` BFS JOIN 富化、`translator` SQL 翻译（方言抽象）、`metadata` 元数据服务、`seed` 向量灌库。
- `tools/`: 各阶段工具（意图、检索、DSL 生成、校验、翻译、审查、SQL 执行），ReAct 工具集中注册在 `AgentToolRegistry`。
- `service/pipeline/`: 7 阶段管线接口与实现，每阶段一个 Service。
- `planner/`: QueryPlan 规划器（LLM 出计划、引擎调度步骤）。
- `a2a/`: A2A Host 编排、模型分层路由（fast/strong）、HITL SQL 确认、trace 中间件。
- `intent/`: 规则优先的意图分类器。
- `resources/skills/`: Skill Workflow 的 SKILL.md 定义。
- `resources/mapper/dsl/`: MyBatis XML 映射。

## Architecture

- 核心管线（入口 `SemanticDslAgentServiceImpl#nlp2Dsl2SqlAgentV2`）：
  意图识别 → 语义检索 → DSL生成 → DSL校验 → DSL富化 → SQL生成 → SQL审查+执行
- 同一能力有 5 种编排形态，均以 `/aiChat` 为前缀：V2 固定管线、ReAct 工具循环、Planner 工作流、Skill 工作流、A2A Host 多 Agent 对话。
- SQL 只能由 `DslTranslator` 从富化后的 DSL 确定性生成，LLM 从不直接写 SQL。
- JOIN 路径由 `SemanticDslEnricher` BFS 最短路径求解，不手写 JOIN。
- 检索两阶段：Embedding Top-K 召回 + Rerank 重排（`DslRetriever`）。

## Backend Rules

- 构造器注入配合 Lombok `@RequiredArgsConstructor`，不用字段 `@Autowired`。
- 日志用 `@Slf4j`，关键阶段用 `━━━ [阶段名] ... ━━━` 分隔符便于追踪。
- 业务异常用 `Nlp2dsl2sqlException`；V2 管线内部失败转成 SSE 错误文本返回，不向上抛栈。
- A2A Host 阶段耗时统一走 `traceRecorder.timedStep(sid, name, detail)` + try-with-resources，`close()` 自动回填 `durationMs` 并打 `[Trace] sessionId/phase/durationMs` 结构化日志；时间点事件才用 `step()`。
- LLM 调用统一 `OpenAIChatModel.stream()` + `blockLast()` 聚合取全文本；DeepSeek 不支持 json_schema，结构化输出用 `ResponseFormat.jsonObject()` + 提示词内嵌格式说明。
- LLM 返回的 JSON 先经 `extractJson` 截取花括号子串再解析，解析失败必须有兜底分支。
- SSE 接口签名固定为 `Flux<String>` 加 `produces = TEXT_EVENT_STREAM_VALUE`。

## Pipeline Rules

- 必须按 校验 → 富化 → 翻译 顺序推进，任何一环失败即终止并返回错误文本，禁止带病执行 SQL。
- SQL 执行只走 `SqlExecuteTool` / `SqlExecutionTool`：仅允许 SELECT、拒绝分号、命中黑名单关键字即拒绝、一律 JDBC 参数绑定。
- SQL 审查（`ReviewTool`）不通过时不得执行，须修正后重审。
- 意图识别先走 `RuleIntentClassifier` 规则命中，未命中才调 LLM。
- 新增 SQL 方言：继承 `AbstractDslTranslator` 实现，并在 `application.yaml` 的 `dsl.translator.dialect` 切换。

## API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/aiChat/nlp2Dsl2SqlAgentV2` | GET (SSE) | V2 语义管线（7 阶段） |
| `/aiChat/nlp2Dsl2SqlAgent` | GET (SSE) | ReAct Agent 工具循环 |
| `/aiChat/nlp2Dsl2SqlPlannerWorkflow` | GET (SSE) | Planner 规划工作流 |
| `/aiChat/nlp2Dsl2SqlAgentSkillWorkflow` | GET (SSE) | Skill 工作流 |
| `/aiChat/a2aHost` | GET (SSE) | A2A Host 对话（含 SQL 确认事件） |
| `/aiChat/a2aHost/confirm` | POST (JSON) | SQL HITL 确认/拒绝 |
| `/aiChat/a2aHost/traces` | GET (JSON) | Host trace 列表 |
| `/aiChat/a2aHost/traces/{sessionId}` | GET (JSON) | 单条 trace 详情 |

另有 A2A Server 卡片端点：`/.well-known/agent-card.json`。

## Config And Data

- 配置集中在 `src/main/resources/application.yaml`。
- 外部依赖：LLM `api.deepseek.com`、Embedding `localhost:8082`（bge-m3）、Rerank `localhost:8083`（bge-reranker-v2-m3）、PostgreSQL `localhost:5432/agent_db`（schema `agent`）。
- 元数据表均在 `agent` schema：`dsl_entity` / `dsl_attribute` / `dsl_metric` / `dsl_metric_attribute` / `dsl_metric_dimension` / `dsl_dimension` / `dsl_dimension_value` / `dsl_relation` / `dsl_filter` / `dsl_synonym`，建表脚本为根目录 `ai_agent.sql`。
- A2A trace JSONL 写入 `.agentscope/traces/a2a-host.jsonl`，容量与 TTL 由 `a2a.host.trace.*` 控制。
- 启动前置条件：数据库已执行 `ai_agent.sql`，Embedding/Rerank 服务已就绪，`agentscope.openai.api-key` 已配置。

## Never Do

- 不要让 LLM 直接生成、修改或拼接 SQL。
- 不要绕过 validator/enricher 直接翻译或执行查询。
- 不要在 SQL 中字符串拼接用户输入，必须参数绑定。
- 不要在业务代码引入 Jackson 解析序列化（Spring MVC 框架层除外）。
- 不要硬编码密钥、Token、数据库密码到源码或提交 Git。
- 不要 catch 异常后静默忽略，至少 log.warn/error 记录。
- 不要新增绕过 `/aiChat` 统一前缀和 SSE 契约的查询入口。

## More Rules

- 多 Agent 架构设计与演进背景：`docs/MULTI_AGENT_DESIGN.md`
