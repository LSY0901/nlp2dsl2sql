# AgentScope A2A Host 复合协作设计

> 日期：2026-07-27  
> 状态：已批准  

> 目标：按 **AgentScope 官方 A2A 写法**，在本项目落地 Host Agent 复合意图协作：  
> 拆解用户问题 → 经 `A2aAgent.call` 分别调用 SQL A2A Server 与 SOP A2A Server → Host 汇总回答。  
> 覆盖示例：「六年级最高分是谁？最高分奖励是什么」。

## 1. 背景与目标

### 1.1 现状

| 能力 | 位置 | 协议 |
|------|------|------|
| NLP→DSL→SQL 问数 | 本项目（V2 / ReAct / Skill 等） | 自定义 `/aiChat/*` SSE |
| SOP 文档问答 | `http://localhost:9002/chat/stream` | 普通 GET SSE，**非 A2A** |
| AgentScope A2A | 未引入 | — |

### 1.2 目标

1. **本项目**：把 SQL 问数能力暴露为 AgentScope **A2A Server**
2. **9002 项目**：暴露标准 **A2A Server**（AgentCard + 可被 Java `A2aAgent` 调用）
3. **本项目 Host**：`HarnessAgent` / `ReActAgent`，通过 **`A2aAgent.call`** 调两个远端 Agent
4. 支持复合问题拆解：数据子问 → SQL Agent；规范子问 → SOP Agent；再合成
5. 新增独立 SSE 入口演示；**不改**现有四个 `/aiChat` 端点行为

### 1.3 已确认决策

| 决策点 | 选择 |
|--------|------|
| 协作语义 | 复合意图拆解（可同题调用多个 Agent） |
| 协议要求 | **必须符合 AgentScope A2A 写法** |
| 双边形态 | **C**：SQL 与 SOP 均为标准 A2A Server |
| Host 形态 | **A**：HarnessAgent / ReActAgent + 工具封装 `A2aAgent.call` |
| 入口 | 新建 `GET /aiChat/a2aHost` |
| 旧 SOP SSE | 本项目 A2A 路径 **不再**调用 `/chat/stream` |

### 1.4 非目标（首版）

- 不上 Nacos 注册发现（WellKnown 直连即可）
- 不并行调用子 Agent（串行，演示清晰）
- 不用 WebClient 直连旧 SSE 冒充 A2A
- 不把手写 Google A2A 协议栈替代 AgentScope 封装
- 不改造现有 V2 / ReAct / Planner / Skill 对外行为
- Harness `subagent.url`（Agent Protocol 远程任务）**不作为**本方案的 A2A 实现

---

## 2. 架构

```
                 GET /aiChat/a2aHost?question=...
                              │
                              ▼
              ┌───────────────────────────────┐
              │ Host Agent（本项目）            │
              │ HarnessAgent / ReActAgent       │
              │ sysPrompt: 拆解 → 分派 → 汇总   │
              │ Tools:                         │
              │  call_sql_agent(query)         │
              │  call_sop_agent(query)         │
              └───────────────┬───────────────┘
                              │ 内部均走 A2aAgent.call
              ┌───────────────┴───────────────┐
              ▼                               ▼
 ┌────────────────────────┐     ┌────────────────────────┐
 │ sql-query-agent        │     │ sop-doc-agent          │
 │ A2A Server（本机 8079） │     │ A2A Server（9002）     │
 │ starter 暴露 ReActAgent│     │ Python/任意 A2A 实现   │
 │ 内部：问数 Tool/管线    │     │ 内部：SOP 文档问答     │
 │ /.well-known/agent-card│     │ /.well-known/agent-card│
 └────────────────────────┘     └────────────────────────┘
```

### 2.1 示例轨迹

原问：`六年级最高分是谁？最高分奖励是什么`

1. Host 拆成两子问  
2. `call_sql_agent("六年级最高分是谁？")` → A2A → sql-query-agent  
3. `call_sop_agent("最高分奖励是什么？")` → A2A → sop-doc-agent  
4. Host 基于两段 tool result 生成最终回答  

---

## 3. AgentScope 写法（硬性约束）

### 3.1 客户端（官方形态）

```java
A2aAgent sqlAgent = A2aAgent.builder()
    .name("sql-query-agent")
    .agentCardResolver(new WellKnownAgentCardResolver(
        sqlBaseUrl, "/.well-known/agent-card.json", Map.of()))
    .build();

Msg result = sqlAgent.call(new UserMessage(subQuery)).block();
```

SOP 同理。Host 的 `@Tool` 只做薄封装，**禁止**在工具内改调 `/chat/stream`。

### 3.2 服务端（本机 SQL）

优先：

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-spring-boot-starter-a2a-server</artifactId>
  <version>${agentscope.version}</version>
</dependency>
```

```yaml
agentscope:
  a2a:
    server:
      enabled: true
      card:
        name: sql-query-agent
        description: 自然语言业务数据查询（NLP→DSL→SQL）
```

备选：手写 `AgentScopeA2aServer` + 框架 Controller 转发 `TransportWrapper`。

SQL A2A Server 背后的 `ReActAgent`：复用现有问数 Toolkit / Pipeline，不另起第三套算法。

### 3.3 为何不用 Harness `subagent.url`

文档中 `SubagentDeclaration.url` 走远程任务 / Agent Protocol 客户端，**不等于** A2A。  
本实战要求协议层必须是 **`io.agentscope.core.a2a.agent.A2aAgent`**。

---

## 4. 模块与接口

### 4.1 本项目新增

| 层级 | 类 | 职责 |
|------|-----|------|
| Controller | `A2aHostController` | `GET /aiChat/a2aHost`，入参 `Nlp2DslAgentRequest`，出参 `Flux<String>` |
| Service | `IA2aHostService` / `A2aHostServiceImpl` | 启动 Host 流式，映射 SSE |
| Agent | `A2aHostAgentConfig` | 装配 Host Harness/ReAct + Toolkit |
| Tools | `A2aRemoteAgentTools` | `call_sql_agent` / `call_sop_agent`，内部 `A2aAgent.call` |
| Config | `A2aClientProperties` | sql/sop base-url、card-path、timeout |
| Prompt | Host sysPrompt | 拆解规则、调用顺序、合成要求 |

### 4.2 对外接口

```
GET /aiChat/a2aHost?question=xxx
Accept: text/event-stream
```

另：A2A Server 的 AgentCard / JSON-RPC 由 starter 自动挂载（与 `/aiChat` 并存）。

### 4.3 Host Prompt 要点

- 识别复合问，拆成可独立回答的子问题（建议 ≤5）
- 数据/指标/明细 → 只通过 `call_sql_agent`
- SOP/规范/奖励/流程 → 只通过 `call_sop_agent`
- 闲聊可直接答，不调远程
- 必须基于 tool 结果作答，不编造数据
- 一侧失败时说明失败项，仍尽量回答成功侧

### 4.4 Tool 契约

```text
call_sql_agent(query: String): String
call_sop_agent(query: String): String
```

实现：`a2aAgent.call(new UserMessage(query)).block()` → 提取文本；捕获异常返回可读错误串。

---

## 5. 依赖（`pom.xml`，版本 `2.0.0`）

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-a2a-client</artifactId>
  <version>${agentscope.version}</version>
</dependency>
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-spring-boot-starter-a2a-server</artifactId>
  <version>${agentscope.version}</version>
</dependency>
```

（若 starter artifact 名以仓库为准有差异，实现时按 Maven Central / 官方文档校正，保持同一 `agentscope.version`。）

---

## 6. 配置

```yaml
agentscope:
  a2a:
    server:
      enabled: true
      card:
        name: sql-query-agent
        description: 自然语言业务数据查询 Agent

a2a:
  sql-agent:
    base-url: http://127.0.0.1:8079
    agent-card-path: /.well-known/agent-card.json
  sop-agent:
    base-url: http://127.0.0.1:9002
    agent-card-path: /.well-known/agent-card.json
    timeout-ms: 60000
```

本机 Host 通过 A2A **回环调用**本机 SQL Server，用于演示完整协议闭环（也可用进程内直调，但首版推荐回环以符合「两边都是 A2A」叙事）。

---

## 7. 9002（SOP）改造清单

| 项 | 要求 |
|----|------|
| 协议 | 标准 A2A Server |
| 发现 | `GET /.well-known/agent-card.json` |
| 调用 | 可被本项目 `A2aAgent.call(UserMessage)` 成功调用 |
| 能力 | 按子问题检索 SOP 并回答 |
| 旧接口 | `/chat/stream` 可保留；A2A Host **不依赖**它 |
| 技术 | AgentScope Python A2A 或任意兼容 A2A Spec 的实现 |

本仓库设计文档只约束联调契约；9002 具体代码在另一项目实现。

---

## 8. 错误处理

| 场景 | 行为 |
|------|------|
| question 为空 | SSE：`错误: 问题不能为空` |
| AgentCard 拉取失败 | 对应 tool 返回错误；Host 继续另一侧 |
| `A2aAgent.call` 超时/失败 | tool 返回错误串；Prompt 要求部分作答 |
| 两侧都失败 | Host 明确说明无法完成 |
| Host 自身异常 | SSE 系统错误 |

---

## 9. SSE 体验

复用现有 ReAct 流式映射：

- 可见 Host 推理 / tool 调用开始与结果摘要（若事件模型支持）
- 最终自然语言结论流式输出

演示时日志应能看到对 sql-agent、sop-agent 的 A2A 调用，而非 `/chat/stream`。

---

## 10. 测试验收

1. 复合问：最终回答同时含「最高分是谁」与「奖励是什么」  
2. 协议：联调/日志证明走 AgentCard + `A2aAgent`，无 `/chat/stream`  
3. 单意图问数 / 单意图 SOP 可用  
4. 停 9002：SQL 侧仍可用，SOP 失败有说明  
5. 旧四个端点回归通过  

---

## 11. 包结构示意

```
org.example.nlp2dsl2sql/
├── controller/
│   └── A2aHostController.java
├── a2a/
│   ├── A2aRemoteAgentTools.java      # call_sql_agent / call_sop_agent
│   ├── A2aHostAgentConfig.java       # Host Agent Bean
│   ├── A2aClientConfig.java          # 两个 A2aAgent Bean
│   └── A2aPromptTemplates.java
├── config/
│   └── A2aClientProperties.java
├── service/
│   ├── IA2aHostService.java
│   └── impl/A2aHostServiceImpl.java
└── （A2A Server）
    └── 由 starter 自动配置；SQL ReActAgent Bean 供 server 使用
```

---

## 12. 成功标准

- 写法可对照 [AgentScope A2A 文档](https://java.agentscope.io/v2/zh/integration/protocol/a2a.html) 逐项落库  
- 复合问题由 Host 经 A2A 调度两个专业 Agent 完成  
- 9002 未就绪时，本项目仍可先用「仅 SQL A2A Server + Host」自测回环  

---

## 13. 相对早期草稿的变更

| 项 | 早期（自研编排 / WebClient） | 本版 |
|----|------------------------------|------|
| 协议 | HTTP SSE 透传 | AgentScope `A2aAgent` + A2A Server |
| SOP | `/chat/stream` | 必须 A2A Server |
| Host | Spring 手写编排 | ReAct/Harness + A2A tools |
| 端点 | `/a2aDecompose` 等 | `/aiChat/a2aHost` |
