# A2A Host SQL 执行前 HITL 确认设计

> 日期：2026-07-30  
> 状态：已批准  
> 实现计划：`docs/superpowers/plans/2026-07-30-a2a-host-sql-hitl.md`  
> 相关：`2026-07-27-a2a-agentscope-host-design.md`

> 目标：在 **A2A Host** 模式中，意图拆分后需要调用 nlp2dsl2sql（SQL）Agent 时，在真正执行 SQL 之前接入 AgentScope HITL（Permission ASK）。页面展示待执行 SQL，用户输入 `yes` 或 `确认` 后才执行；否则取消该 SQL 子查询。

---

## 1. 背景与目标

### 1.1 现状

| 能力 | 位置 | 说明 |
|------|------|------|
| A2A Host SSE | `GET /aiChat/a2aHost` | Host 拆意图后 `call_sql_agent` / `call_sop_agent` |
| SQL 子问 | `A2aRemoteAgentTools#callSqlAgent` | 阻塞 `A2aAgent.call` → 本机 A2A `sql-query-agent` |
| 真正执行 | `AgentToolRegistry#executeSql` | 无人工确认，审查通过后直接跑库 |

Host 对 SQL Agent 走 A2A 阻塞调用时，中间的 Permission / HITL 事件无法自然回到前端 SSE。

### 1.2 目标

1. **仅 A2A Host 模式**启用 SQL 执行前确认
2. 使用 AgentScope **Permission HITL**（`execute_sql` → `ASK`）
3. 页面展示 SQL；用户输入 **`yes` 或 `确认`** 才批准执行
4. 拒绝 / 非确认输入 / 超时 → **取消该 SQL 子查询**，不执行；Host 汇总时说明未确认；SOP 等其它子问可继续
5. 多 SQL 子问：**串行**分别确认（首版不做汇总一次确认）

### 1.3 已确认决策

| 决策点 | 选择 |
|--------|------|
| 作用范围 | 仅 A2A Host（方案 A） |
| 拒绝策略 | 取消该 SQL 子查询（方案 A） |
| 确认方式 | 必须输入 `yes` 或 `确认`（去空格、忽略大小写） |
| 多 SQL | 串行分别确认（方案 C） |
| 技术路径 | Host 内 SQL 改走本地 `streamEvents` + Permission HITL（方案 1） |

### 1.4 非目标（首版）

- 不改造 V2 / ReAct / Planner / Skill 等其它 `/aiChat/*` 行为
- 不改造对外 A2A Server 的 `sqlQueryReActAgent`（无 HITL）
- 不允许用户编辑 SQL 后再执行
- 不汇总多条 SQL 一次确认
- 不做跨 A2A 的 HITL 桥接

---

## 2. 架构

```
页面 (A2A Host 模式)
  │  GET /aiChat/a2aHost?question=...&sessionId=...   (SSE)
  ▼
A2aHostService
  │  Host HarnessAgent 拆意图
  ├─ SOP 子问 → 仍 A2aAgent.call(sop)（不变）
  └─ SQL 子问 → 本地 sqlQueryHitlAgent.streamEvents（不再经 A2A）
                    │
                    ├─ 正常工具 / 文本事件 → 映射进 Host SSE
                    └─ RequireUserConfirmEvent (execute_sql)
                         │ SSE 推送确认片段（含 sql）
                         ▼
                       页面展示 SQL + 确认输入
                         │ POST /aiChat/a2aHost/confirm
                         ▼
                       ConfirmResult 恢复 Agent
                         ├─ approved → 执行 SQL → 继续管线 → 返回 Host
                         └─ denied  → 工具结果=用户取消 → Host 汇总说明
```

### 2.1 示例轨迹

原问：`六年级最高分是谁？最高分奖励是什么`

1. Host 拆成两子问  
2. `call_sql_agent("六年级最高分是谁？")` → 本地 HITL Agent  
   - 生成并审查 SQL 后触发 `execute_sql` ASK  
   - SSE 推送待确认 SQL；用户输入 `确认` → 执行 → 返回查询结论  
3. `call_sop_agent("最高分奖励是什么？")` → 原 A2A SOP  
4. Host 汇总最终回答  

若步骤 2 用户输入其它内容：不执行 SQL，工具返回「用户未确认，已取消」；Host 仍可基于 SOP 结果作答并说明数据侧已取消。

---

## 3. 后端设计

### 3.1 HITL SQL Agent Bean

- 新增 Bean，建议名：`sqlQueryHitlAgent`（`ReActAgent`）
- 复用现有问数 `Toolkit` / `AgentToolRegistry` 与 Supervisor Prompt
- `PermissionContextState`：
  - `mode = DEFAULT`
  - `execute_sql` → `ASK`（`PermissionRule`，`ruleContent=null` 匹配全部调用）
  - 其它问数工具（检索、DSL、翻译、`review_sql` 等）→ `ALLOW`
- **不修改** `sqlQueryReActAgent`（A2A 对外仍无 HITL）

### 3.2 Host 工具改造

- `A2aRemoteAgentTools#callSqlAgent`（或抽到专用委托类，避免工具类过长）：
  - A2A Host 场景改为驱动 `sqlQueryHitlAgent.streamEvents`
  - **SSE 桥接（写死）**：每次 Host SSE 请求在 Service 层创建 `Sinks.Many<String>`（或等价队列），经 `RuntimeContext` / ThreadLocal / 显式参数注入到 SQL 工具调用路径；`callSqlAgent` 将 HITL Agent 的文本增量与确认标记 `tryEmitNext` 到该 Sink，Host 的 `Flux` 与 Sink 合并推给前端
  - 遇 `RequireUserConfirmEvent`：写入挂起表并 **阻塞等待** `CompletableFuture`（工具线程可接受阻塞；勿在 Reactor 事件线程 `block`）
  - 确认接口完成 Future 后：构造 `ConfirmResult`，调用 `agent.call(resumeMsg)` 恢复；拒绝则 `approved=false`
  - 将 HITL Agent 最终文本作为 `call_sql_agent` 的 tool result 返回 Host
- `call_sop_agent` 保持 `A2aAgent.call` 不变

### 3.3 会话与挂起状态

- **sessionId 写死策略**：前端必传；扩展 `Nlp2DslAgentRequest` 增加 `sessionId` 字段（GET 查询参数绑定）。若缺失，服务端生成 UUID，并在 SSE 首包输出 `sessionId: <id>` 行供前端回填
- 内存挂起表（进程内即可）：

```text
sessionId → PendingSqlConfirm {
  toolUseBlocks,
  agent / 可恢复句柄,
  CompletableFuture<Boolean> decision,
  createdAt
}
```

- 超时：**5 分钟**未确认 → 按拒绝完成 Future 并清理
- SSE 中断：挂起保留至超时；**绝不**因断线自动执行 SQL

### 3.4 API

#### 3.4.1 流式对话（扩展）

- `GET /aiChat/a2aHost`
- 入参：`Nlp2DslAgentRequest`（`question` + `sessionId`）
- 出参：SSE 文本流（含 HITL 确认标记）

#### 3.4.2 确认接口（新增）

- `POST /aiChat/a2aHost/confirm`
- 入参 VO：`A2aHostConfirmRequest`
  - `sessionId`：必填
  - `approved`：必填 boolean
  - `rawInput`：必填（用户原始输入，供服务端二次校验与日志）
- 出参 VO：`A2aHostConfirmResponse`
  - `ok`：boolean
  - `message`：说明
- **批准判定写死（服务端为准）**：对 `rawInput` trim 后忽略大小写，仅当等于 `yes` 或等于 `确认` 时视为批准；否则视为拒绝。请求体中的 `approved` 仅作前端意图提示，**不得**单独作为放行依据（防止绕过输入校验）

Controller 入参/出参必须使用明确类型，不得用 `Map` 直接接参。

### 3.5 SSE 确认事件格式

文本标记协议（兼容现有纯文本 SSE 渲染）：

```text
========== [SQL确认待审批] ==========
sessionId: <sessionId>
toolCallId: <id>
sql:
<SQL 原文>
========== [请输入 yes 或 确认] ==========
```

前端优先按标记解析；首版可不强制 JSON 行。

批准 / 拒绝后可推送：

```text
========== [SQL确认结果] approved=true ==========
```

或 `approved=false` / `timeout`。

### 3.6 与 AgentScope HITL 的对应关系

| 步骤 | AgentScope 机制 |
|------|-----------------|
| 拦截 `execute_sql` | `PermissionContext` ASK 规则 |
| 流式获知待确认 | `RequireUserConfirmEvent` |
| 恢复执行 | `ConfirmResult` + `Msg.METADATA_CONFIRM_RESULTS` → `agent.call` |
| 拒绝 | `ConfirmResult(false, toolCall)` → 不执行工具 |

---

## 4. 前端设计（`nlp2dsl2sqlV2.html`，仅 A2A Host）

1. 切换到 A2A Host 或首次发送时生成并持有 `sessionId`
2. SSE URL 带上 `sessionId` 与 `question`
3. 解析到 `[SQL确认待审批]`：
   - 展示只读 SQL 确认区 + 输入框
   - 占位符：「输入 yes 或 确认」
   - 暂停将主输入当作新问题发送（确认态）
4. 提交逻辑：将 `rawInput` 原样 POST；前端可本地预判提示，但**以服务端对 rawInput 的判定为准**
5. `POST /aiChat/a2aHost/confirm` 成功后收起确认区，继续消费同一条 SSE
6. 多 SQL：再次出现待审批标记时重复上述 UI

---

## 5. 错误与边界

| 场景 | 处理 |
|------|------|
| 确认超时（5 分钟） | 服务端按拒绝；SSE 提示确认超时已取消 |
| 无挂起却确认 | `ok=false`，提示无待确认 SQL |
| sessionId 错配 | 拒绝确认，提示刷新重试 |
| 用户拒绝 | 不执行 SQL；Host 汇总说明 |
| 确认接口瞬时失败 | 前端可重试；挂起保留至超时 |
| SSE 断开 | 不自动执行；超时清理挂起 |

---

## 6. 代码落点（预期）

| 区域 | 变更 |
|------|------|
| `a2a/SqlQueryA2aServerAgentConfig` 旁 | 新增 HITL Agent 配置类 / Bean |
| `a2a/A2aRemoteAgentTools` | SQL 路径改为本地 HITL 流式 + 等待确认 |
| `service/impl/A2aHostServiceImpl` | sessionId、确认事件映射、与挂起桥接 |
| `controller/A2aHostController` | 扩展 chat 入参；新增 confirm |
| `models/vo` | `sessionId`、Confirm Request/Response |
| `static/nlp2dsl2sqlV2.html` | A2A Host 确认 UI 与输入校验 |

方法需有注释；独立逻辑封装；单方法避免过长；Controller 明确入参出参。

> 注：项目运行时为 Java 21 + Spring Boot 3.x（见 `AGENTS.md`）。编码风格跟随本仓库现有 AgentScope 代码；用户规则中的 Java 8 / Boot 2.x 以本仓库实际栈为准。

---

## 7. 测试计划

- [ ] 单 SQL 子问：SSE 出现待确认 → 输入 `确认` → 执行并返回结论
- [ ] 输入 `yes`（大小写混合）→ 批准
- [ ] 输入其它文本 → 不执行，Host 说明已取消
- [ ] 复合问：SQL + SOP；拒绝 SQL 后 SOP 仍可汇总
- [ ] 两段 SQL 子问：串行两次确认
- [ ] 超时未确认 → 取消且不落库执行
- [ ] 其它模式（V2/ReAct 等）行为无回归
- [ ] A2A 对外 `sql-query-agent` 仍可直接执行（无 HITL）

---

## 8. 成功标准

1. A2A Host 路径下，任意 `execute_sql` 在用户确认前不会访问业务库执行
2. 仅 `yes` / `确认` 可批准；拒绝与超时均取消该子查询
3. 使用 AgentScope Permission HITL（非自研假暂停）
4. 前端在 A2A Host 模式可完成确认闭环
5. 其它入口与对外 A2A SQL Agent 行为不变
