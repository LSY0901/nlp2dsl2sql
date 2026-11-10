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
mvn clean compile              # 改完代码至少跑这个
mvn test                       # 82 个单测：validator / translator / HITL / trace / 会话记忆
mvn clean package -DskipTests  # 打包
java -jar target/Nlp2dsl2sql-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

访问：`http://localhost:8079/nlp2dsl2sqlV2.html`，默认端口 `8079`。
DB 口令在未入库的 `application-local.yaml`，本地启动需带 `--spring.profiles.active=local`（或设 `DB_PASSWORD` 环境变量）。

## Project Structure

- `semanticdsl/`: 语义层核心。`validator` DSL 校验、`enricher` BFS JOIN 富化、`translator` SQL 翻译（方言抽象）、`metadata` 元数据服务、`seed` 向量灌库。
- `tools/`: 各阶段工具（意图、检索、DSL 生成、校验、翻译、审查、SQL 执行），ReAct 工具集中注册在 `AgentToolRegistry`。
- `service/pipeline/`: 7 阶段管线接口与实现，每阶段一个 Service（目前仅 Planner 引擎在用）。
- `service/memory/`: 会话记忆分层（见 ADR-0003）。`QueryPreprocessor` 是唯一入口；短期走 `SessionSnapshotStore`（Redis 快照+轮次），长期经 `MemoryArchiveWriter` 流水写穿 PG，`MemorySummaryJob`/`MemoryProfileJob` 定时 LLM 压缩出摘要与画像，`QueryContextRewriter` 改写时注入画像。
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
- 多轮记忆分三层（ADR-0003）：短期 Redis（快照+轮次，追问消歧与槽位继承，`QueryPreprocessor` 确定性可测）；长期 PG（流水→LLM 摘要→用户画像，画像注入改写 prompt）；AgentScope 内置 memory（模型自用 MEMORY.md，并存不读回）。快照只存问句与语义 DSL，不存查询结果。
- 5 种编排形态共享同一份 `QueryPreprocessor` 与同一份语义快照：它们共用同一套 DSL 生成工具与提示词，产物模式无关。前端 sessionId 落 localStorage，切模式与刷新都不清记忆。

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
- 新增 SQL 方言：继承 `AbstractDslTranslator` 实现，并在 `application.yaml` 的 `dsl.translator.dialect` 切换。当前**仅实现 PostgreSQL**，配置其它值会启动失败（`SqlDialect.fromValue` 抛异常，不静默回退）。

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
| `/aiChat/memory/session/{sessionId}` | GET (JSON) | 会话记忆查看（快照+轮次+摘要） |
| `/aiChat/memory/user/{userId}` | GET (JSON) | 用户长期画像查看 |

另有 A2A Server 卡片端点：`/.well-known/agent-card.json`。

## Config And Data

- 配置集中在 `src/main/resources/application.yaml`。
- 外部依赖：LLM `api.deepseek.com`、Embedding `localhost:8082`（bge-m3）、Rerank `localhost:8083`（bge-reranker-v2-m3）、PostgreSQL `localhost:5432/agent_db`（schema `agent`）、Redis `localhost:6379`（Docker 容器 `nlp2dsl2sql-redis`，仅绑定 127.0.0.1、数据卷 `nlp2dsl2sql-redis-data`，承接会话短期记忆）。
- 元数据表均在 `agent` schema：`dsl_entity` / `dsl_attribute` / `dsl_metric` / `dsl_metric_attribute` / `dsl_metric_dimension` / `dsl_dimension` / `dsl_dimension_value` / `dsl_relation` / `dsl_filter` / `dsl_synonym`，建表脚本为根目录 `ai_agent.sql`。会话记忆长期表 `memory_session_archive` / `memory_session_summary` / `memory_user_profile`，建表脚本为根目录 `memory.sql`。
- A2A trace JSONL 写入 `.agentscope/traces/a2a-host.jsonl`（`.agentscope/` 已 gitignore 且不入库）：每行一次调用耗时记录（`llm-call` / `tool-call`，带 sessionId/agentName 区分 host/sql 层，LLM 行含 token 用量），其余事件不落盘；内存 trace 另含 steps/HITL/SQL 明细，容量/TTL/刷盘由 `a2a.host.trace.*` 控制。
- A2A HITL：挂起按 confirmId 键控（SSE 卡片回传），确认超时由 `a2a.host.confirm.timeout-ms` 控制（默认 5 分钟，超时按拒绝）；SSE 断开/异常时该会话挂起立即作废（断开即作废，不盲执行）。
- 记忆分层铁律：短期记忆读写全在 Redis（键前缀 `nlp2dsl2sql:memory:`，快照 TTL 由 `memory.session-ttl` 控制，默认 24h）；长期记忆读写全在 PG，不跨层缓存。长期任务与注入由 `memory.longterm.*` 控制。
- 启动前置条件：数据库已执行 `ai_agent.sql` 与 `memory.sql`，Embedding/Rerank 服务已就绪，Redis 容器 `nlp2dsl2sql-redis` 已启动，`agentscope.openai.api-key` 已配置。

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
- 关键决策记录：`docs/adr/`

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five canonical roles (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context (one `CONTEXT.md` + `docs/adr/` at the repo root). See `docs/agents/domain.md`.
