# 企业级就绪度整改计划（定稿，2026-09-29）

> grill 会话 Q1–Q13 收敛结论落盘，后续作业从这里继续。约束：1 人 x 2 周，只动本仓，形态 = 单机 Docker + 外置配置。

## 1. 目标与非目标

- 终点优先级：A 生产可上线 > B 进公司流程 > C 可维护；D 扛量（多实例/QPS）本次不做。
- 风险序：A 密钥泄露 > B SSE 卡死 > C 记忆丢失 > D 磁盘打满。
- 非目标（下批）：K8s yaml、多副本 Redis 共享记忆、OTEL/Prom 全套、Testcontainers 集成测试、完整 Spring Security/OAuth、限流/审计日志。

## 2. 差距全景（审计证据）

1. **安全爆雷**：`src/main/resources/application.yaml:10,16,40,44` 明文 `password:123456 + 3 处 sk-8aea...` 已入库；`.gitignore` 无敏感忽略；`config/CorsConfig.java:16-21` 为 `* + allowCredentials(true)`；`pom.xml` 无 spring-security，`/aiChat/*` 全开放。
2. **可观测裸奔**：`pom.xml` 无 actuator/micrometer/otel；无 `logback.xml`，无 MDC traceId；自研 `a2a/trace/HostTraceRecorder.java` + `.agentscope/traces/a2a-host.jsonl`（已 6M）仅覆盖 A2A Host，V2/ReAct/Planner/Skill 无 `timedStep`，重启全丢，无轮转。
3. **韧性为 0**：`config/EmbeddingClient.java:26 / RerankClient.java:23` 裸 `RestClient.build()` 无超时重试；5 个 SSE 全 `blockLast()` 无界等待；`tools/ReviewTool.java:76-78` 解析失败默认放行（fail-open）。
4. **交付手工化**：无 Dockerfile/compose/CI/K8s；无 Flyway（`Grep flyway|liquibase` 0 命中），靠根目录 `ai_agent.sql(672 行)` 手工重建；36 单测全纯单元，`tools/ pipeline/ controller/ mapper/` 零覆盖。
5. **架构复制式复用**：`buildReviewSchema x3`（TranslationTool / TranslationPipelineServiceImpl / SemanticDslAgentServiceImpl）、`callLlm+extractJson x3`、`mapEventToSseChunk x4` 逐字复制；`A2aHostServiceImpl:90-110` 主路不调 `QueryPreprocessor`，SOP 问句不进快照；`SemanticSessionStore` 为单机 `ConcurrentHashMap`，多实例失忆。

## 3. 决策收敛

- Q5 配置外置：`application.yaml` 全改 `${...}` 占位；真实值走 `src/main/resources/application-local.yaml`（gitignore，不入库）；同时提交 `application-local.yaml.example` 模板；缺文件启动 fail-fast。本地启动固定 `--spring.profiles.active=local`。
- Q6 安全最小闭环：换掉已泄露 sk（手动）+ CORS 收紧到具体域名 + `/aiChat/*` 极简 API-Key 过滤器（header 对不上 401）。不限流审计下批。
- Q7 观测最小集：`actuator health/info/metrics + logback.xml MDC traceId(sessionId) + timedStep` 推广到 V2 管线 7 阶段；JSONL 先 `.gitignore + 按大小轮转`，不迁库。
- Q8 韧性：`RestClient/OpenAIChatModel` 加 `5s connect / 60s read + 重试 2 次`；所有 `blockLast()` 加 60s timeout 转 SSE 错误帧；`ReviewTool` 改 fail-closed（解析失败拒绝执行）。
- Q9 交付：多阶段 Dockerfile + `docker-compose`（PG only，Embedding/Rerank/LLM 仍连外部）+ Flyway（`ai_agent.sql` 原样作 `V1__baseline.sql`）+ GitHub Actions 只跑 `mvn test`。
- Q10 顺序：P0 安全配置（1–3 天）> P1 韧性观测（4–6 天）> P2 交付（7–10 天），留 4 天 buffer。
- Q11 手动动作（用户）：① DeepSeek 换 key ② 本地用 `application-local.yaml` + `--spring.profiles.active=local` ③ 现有 `agent_db` 首跑 Flyway 开 `baselineOnMigrate=true`。
- Q12 breaking：同意不兼容老调用（fail-closed 报错、前端加 header、CORS 白名单），`nlp2dsl2sqlV2.html` 同步改。
- Q13：本文件即定稿，开工从 P0 起。

## 4. 执行清单

### P0 安全 + 配置（Day 1–3）
- [ ] `.gitignore` += `.agentscope/`、`application-local.yaml`、`*.jsonl`；确认已提交的 `6.7M .agentscope` 不再跟踪（`git rm --cached`）。
- [ ] `application.yaml` 敏感项改占位：datasource password → `${DB_PASSWORD:}`；`agentscope.openai.api-key` → `${LLM_API_KEY:}`；embedding/rerank/a2a base-url → `${...:http://localhost:...}` 默认。
- [ ] 新增 `src/main/resources/application-local.yaml`（不入库，本地真实值）+ `application-local.yaml.example`（入库，占位模板）。
- [ ] 缺关键 env/文件启动 fail-fast（`@ConfigurationProperties` 校验或 `ApplicationRunner` 检查，至少 `log.error` + 抛 `Nlp2dsl2sqlException`）。
- [ ] `CorsConfig` 收紧：`allowedOriginPatterns("*")` → 白名单（local 先放 `http://localhost:8079`）。
- [ ] 新增极简 API-Key 过滤器：`X-API-KEY` 对 `${AI_API_KEY:}`（local 文件配），`/aiChat/*` 401；前端 `nlp2dsl2sqlV2.html` 加 header。
- [ ] 用户手动：DeepSeek 后台轮换 `sk-8aea...`。
- 验证：`mvn clean compile` 通过；`java -jar --spring.profiles.active=local` 缺 key 时明确报错；带 key 时 V2 SSE 正常。

### P1 韧性 + 观测（Day 4–6）
- [ ] `EmbeddingClient/RerankClient`：`RestClient` 加 `5s connect / 60s read`；失败重试 2 次（spring-retry 或手写循环，不引 resilience4j）。
- [ ] LLM 调用点（`SemanticDslAgentServiceImpl:277 / DslGeneratePipelineServiceImpl:115 / DslGenerationTool:116 / QueryPlanner`）`blockLast()` → `blockLast(Duration 60s)` + 超时转 SSE 错误帧。
- [ ] `ReviewTool:76-78` fail-open → fail-closed（解析失败 `setResult(false)` + 错误信息），补 1 个单测。
- [ ] `pom.xml` 引 `spring-boot-starter-actuator`，开 `health/info/metrics`。
- [ ] 加 `logback.xml` + `MDC sessionId/traceId`（7 阶段入口 `put`，`finally` 清）；`[Trace] sessionId/phase/durationMs` 保留。
- [ ] `timedStep` 推广到 V2 管线 7 阶段；JSONL 加 `.gitignore` + 按大小轮转（先 10MB 切分，无后台清理任务）。
- 验证：`mvn test` 全绿；构造 Embedding 500ms 延迟仍 2 次重试后降级；LLM hang 60s 后 SSE 收到错误帧不 hang 死。

### P2 交付（Day 7–10）
- [ ] 多阶段 `Dockerfile`（eclipse-temurin:21-jre + fat-jar）+ `docker-compose.yml`（PG + pgvector only，`agent_db` 自动建库，Embedding/Rerank/LLM 连宿主外部）。
- [ ] 引 Flyway：`ai_agent.sql` 原样作 `db/migration/V1__baseline.sql`，`baselineOnMigrate=true`；README 写清现有库首次启动步骤。
- [ ] `.github/workflows/maven.yml`：`mvn test` 门禁。
- [ ] `nlp2dsl2sqlV2.html` 联调：API-Key header + 错误帧展示。
- 验证：`mvn clean package -DskipTests` + `docker compose up` 走通一次端到端问句；`mvn test` 门禁绿。

## 5. 恢复作业入口

- 后续开工直接读本文件 + 跑 `git status`，按 P0→P1→P2 未勾选项继续。
- 关键校验命令：`mvn clean compile` / `mvn test` / `java -jar target/Nlp2dsl2sql-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`。
- 外部依赖：PG `localhost:5432/agent_db`、Embedding `8082`、Rerank `8083`、`api.deepseek.com`。
