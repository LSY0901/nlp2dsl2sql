# ADR-0002：会话记忆分两套，职责按"确定性"切开

## 状态

已采纳

## 背景

项目里同时存在两种能做记忆的东西：

1. **AgentScope Harness 内置 memory**。`HarnessAgent` 的 memory hooks 是开着的，模型自己决定往 `MEMORY.md` 写什么。实际跑下来它确实在写有价值的东西——用户偏好、能力边界，格式是自然语言 markdown。
2. **自建的 `service/memory`**。`SemanticSessionStore` + `QueryContextRewriter`，做"那四年级呢"这类追问的确定性补全。

一开始这两套是打架的：自建那套只挂在 A2A Host 的 SQL 子 agent 上（5 种编排形态里只覆盖 1 种），而框架那套写的东西没有任何代码读回；两边各自演化，谁也不知道对方的最新状态。

## 决策

按**是否需要确定性**切开，不做统一：

| | 追问消歧与槽位继承 | 用户画像与能力边界 |
|---|---|---|
| 载体 | `QueryPreprocessor`（进程内 store） | AgentScope `MEMORY.md` |
| 触发 | 词表规则 + fast LLM 改写 | 模型自行决定何时 flush |
| 可测性 | 纯规则可脱离模型单测 | 不可测 |
| 归属 | 本项目代码 | 框架 |

配套的三条边界：

- **不读回 `MEMORY.md`**。它是给模型自己看的上下文，不是给别人用的数据库；要程序化消费就得先有 schema，而 markdown 没有 schema。
- **5 种编排形态共用一份快照**。它们共用同一套 `tools/AgentToolRegistry` 与提示词，产物是模式无关的，所以不需要按模式隔离记忆——按模式隔离只会让"切模式就失忆"。
- **快照只存问句与语义 DSL，不存查询结果**。复用半小时前的行集当答案，是数据正确性 bug 而不是功能。

## 后果

- 追问消歧变成可测的纯逻辑，`QueryPreprocessorTest` 7 个用例不需要网络；
- 记错了画像没法回滚（模型写的），接受这个代价——画像错了最多是下次问候得啰嗦；
- 快照在进程内，重启即失忆。要跨重启就得建表，当前不需要。

## 关联

- `service/memory/` 全部
- `config/MemoryProperties`（`memory.session-ttl`）
- `AGENTS.md` → Architecture 多轮记忆两条
