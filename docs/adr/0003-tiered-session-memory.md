# ADR-0003: 统一双层会话记忆架构（短期 Redis + 长期压缩 PG，移除本地 Workspace）

- **状态**: 已采纳（2026-11 更新：彻底移除本地 `.agentscope/workspace*` 与 `MEMORY.md`，合并为唯一一套 Redis + PG 双层记忆体系）
- **关联设计**: [`docs/UNIFIED_SESSION_MEMORY_PLAN.md`](../UNIFIED_SESSION_MEMORY_PLAN.md)

## 背景

早期实现中同时存在两套割裂的记忆机制：
1. AgentScope `HarnessAgent` 默认的本地 `.agentscope/workspace*` 文件记忆（`sessions/*.jsonl`、`MEMORY.md`）与进程内 `A2aHostSessionManager` 缓存；
2. 自建 `service/memory`（Redis 存 DSL 快照，PG 归档 SQL 问句并定时压缩摘要与画像）。

两套机制并存导致：多实例无法共享本地文件会话状态、SOP/闲聊轮次与最终回答结论未进 PG 归档、压缩出的 `memory_session_summary` 从未被读回消费、前端复用同一 `sessionId` 时摘要与画像无法增量更新。

## 决策

1. **彻底移除本地 `.agentscope/workspace*` 文件记忆**：
   - `A2aHostAgentFactory` 改用无状态 `ReActAgent` + `SpringRedisAgentStateStore`，废弃进程内 `A2aHostSessionManager`；
   - `Nlp2dsl2sqlAgent` 与 `Nlp2dsl2sqlSkillHarnessAgent` 禁用全部本地 memory hooks / session persistence / workspaceContext / compaction 并统一注入 `SpringRedisAgentStateStore`。

2. **短期记忆层（Redis，统一 TTL = `memory.session-ttl` 默认 24h）**：
   - `nlp2dsl2sql:memory:state:{sessionId}:{agentName}:{stateKey}`：由 `SpringRedisAgentStateStore#forAgent` 按 Agent 命名空间隔离托管 `AgentState` 多轮上下文消息（防止前端同一 `sessionId` 跨 Host / ReAct / Skill 模式切换时互相恢复异构 `tool_call` 消息）；保存时若消息数超过 `memory.max-state-messages`（默认 30），按 `MsgRole.USER` 边界滑动截断，并触发带节流的 `MemorySummaryJob#summarizeSessionAsync`；
   - `nlp2dsl2sql:memory:snapshot:{sessionId}`：最新语义 DSL 快照（仅在 SQL 子查询成功产出 DSL 时通过 `rememberSnapshotOnly` / `rememberTurn` 更新，防止纯 SOP 问答或闲聊冲掉上一轮 SQL 槽位）。完整问答流水不在 Redis 重复存一份，统一由 PG `memory_session_archive` 承载。

3. **长期压缩记忆层（PostgreSQL `agent` schema）**：
   - **写侧（采集）**：`memory_session_archive` 新增 `answer`（超长时保留结尾结论，上限 `memory.answer-max-chars` 默认 500 字）与 `turn_type`（`SQL` / `SOP` / `MIXED` / `CHITCHAT`）；五种编排形态（V2 / Planner / ReAct / Skill / A2A Host）均在流结束（含客户端 `CANCEL`）时汇总调用 `QueryPreprocessor#rememberTurn` 异步写穿落库；
   - **压缩与提炼（增量合并 + 节流双触发 + 增量游标）**：
     - `MemorySummaryJob`：只读取 `created_at > previous.last_turn_at` 的新增轮次与已有摘要一起增量合并（单次成本只与新增轮次数相关，不再随总会话长度线性膨胀）；窗口截断触发受 `memory.longterm.summary.evict-min-new-turns`（默认 5 轮）节流保护，配合 `@Scheduled` 闲置扫描兜底；
     - `MemoryProfileJob`：按 `id > summary_seen OR summarized_at > profile.updated_at` 增量消化新增或更新的会话摘要，提炼四字段 JSON 画像写入 `memory_user_profile`；
   - **读侧（双端消费）**：
     - `A2aHostServiceImpl` 启动时调用 `MemoryProfileService#buildHostLongTermMemoryContext(userId)` 读取该用户的画像与最近 3 条会话摘要，拼入 `A2aHostPrompt.SYSTEM` 尾部（经单测验证 `ReActAgent` 多轮从 Redis 恢复时每轮使用新注入的 `sysPrompt`）；
     - `QueryContextRewriter` 追问消歧改写时注入用户画像。

## 后果

- 全项目僅保留唯一一套「短期 Redis + 长期压缩 PG」记忆架构，无本地磁盘状态残留，天然支持水平扩展与重启恢复；
- 任何 Redis / PG 读写或 LLM 压缩异常均静默降级并记录 `log.warn`，绝不阻断查询主流程。
