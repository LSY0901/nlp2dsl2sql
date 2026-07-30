# A2A Host SQL HITL 确认 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 A2A Host 模式下，SQL 子问本地走带 Permission ASK 的 HITL Agent，执行 `execute_sql` 前经 SSE 展示 SQL，用户输入 `yes`/`确认` 后才执行；否则取消该子查询。

**Architecture:** Host 仍用 `HarnessAgent`；SOP 仍 A2A。`call_sql_agent` 改为每次创建本地 `sqlQueryHitlAgent`（`execute_sql=ASK`，其它问数工具=ALLOW），`streamEvents` 遇 `RequireUserConfirmEvent` 挂起并通过 `Sinks.Many` 桥进 Host SSE；`POST /aiChat/a2aHost/confirm` 按 `rawInput` 判定后 `ConfirmResult` 恢复。

**Tech Stack:** Java 21、Spring Boot 3.5、AgentScope 2.0.0（Permission / ConfirmResult / RequireUserConfirmEvent）、Reactor Flux/Sinks、现有 `nlp2dsl2sqlV2.html`

**Spec:** `docs/superpowers/specs/2026-07-30-a2a-host-sql-hitl-design.md`

---

## File Structure

```
src/main/java/org/example/nlp2dsl2sql/
├── models/vo/
│   ├── Nlp2DslAgentRequest.java          # +sessionId
│   ├── A2aHostConfirmRequest.java        # 新建
│   └── A2aHostConfirmResponse.java       # 新建
├── a2a/
│   ├── A2aHostChatContext.java           # sessionId + SSE Sink
│   ├── A2aSqlConfirmTexts.java           # 批准判定 + SSE 标记格式化
│   ├── PendingSqlConfirm.java            # 挂起快照
│   ├── A2aSqlConfirmRegistry.java        # 挂起表 + 超时
│   ├── SqlQueryHitlAgentFactory.java     # 每次创建带 Permission 的 ReActAgent
│   ├── A2aRemoteAgentTools.java          # callSqlAgent 改本地 HITL
│   └── SqlQueryA2aServerAgentConfig.java # 不改对外 Bean
├── service/
│   ├── IA2aHostService.java              # chat(sessionId,question) + confirm
│   └── impl/A2aHostServiceImpl.java
├── controller/
│   └── A2aHostController.java            # +confirm POST
src/main/resources/static/nlp2dsl2sqlV2.html
src/test/java/org/example/nlp2dsl2sql/a2a/
└── A2aSqlConfirmTextsTest.java
```

---

### Task 1: 确认文本工具 + 单测

**Files:**
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aSqlConfirmTexts.java`
- Create: `src/test/java/org/example/nlp2dsl2sql/a2a/A2aSqlConfirmTextsTest.java`

- [ ] **Step 1: 写失败单测**

```java
package org.example.nlp2dsl2sql.a2a;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class A2aSqlConfirmTextsTest {

    @Test
    void approvesYesAndConfirmIgnoringCaseAndSpaces() {
        assertTrue(A2aSqlConfirmTexts.isApproved("yes"));
        assertTrue(A2aSqlConfirmTexts.isApproved("YES"));
        assertTrue(A2aSqlConfirmTexts.isApproved(" 确认 "));
        assertFalse(A2aSqlConfirmTexts.isApproved("no"));
        assertFalse(A2aSqlConfirmTexts.isApproved("确认执行"));
        assertFalse(A2aSqlConfirmTexts.isApproved(null));
    }

    @Test
    void formatsPendingMarkerWithSql() {
        String text = A2aSqlConfirmTexts.formatPending(
                "sid-1", "tc-1", "SELECT 1");
        assertTrue(text.contains("[SQL确认待审批]"));
        assertTrue(text.contains("sessionId: sid-1"));
        assertTrue(text.contains("toolCallId: tc-1"));
        assertTrue(text.contains("SELECT 1"));
        assertTrue(text.contains("[请输入 yes 或 确认]"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -Dtest=A2aSqlConfirmTextsTest test`

Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现工具类**

```java
package org.example.nlp2dsl2sql.a2a;

/**
 * SQL HITL 确认文案与批准判定。
 */
public final class A2aSqlConfirmTexts {

    public static final String MARK_PENDING = "[SQL确认待审批]";
    public static final String MARK_PROMPT = "[请输入 yes 或 确认]";
    public static final String MARK_RESULT_PREFIX = "[SQL确认结果]";

    private A2aSqlConfirmTexts() {
    }

    /**
     * 服务端批准判定：trim 后忽略大小写，仅 yes 或 确认。
     */
    public static boolean isApproved(String rawInput) {
        if (rawInput == null) {
            return false;
        }
        String t = rawInput.trim();
        return "yes".equalsIgnoreCase(t) || "确认".equals(t);
    }

    /**
     * 组装 SSE 待确认片段。
     */
    public static String formatPending(
            String sessionId, String toolCallId, String sql) {
        return "\n\n========== " + MARK_PENDING + " ==========\n"
                + "sessionId: " + nullToEmpty(sessionId) + "\n"
                + "toolCallId: " + nullToEmpty(toolCallId) + "\n"
                + "sql:\n"
                + nullToEmpty(sql) + "\n"
                + "========== " + MARK_PROMPT + " ==========\n\n";
    }

    /**
     * 组装确认结果片段。
     */
    public static String formatResult(boolean approved, String reason) {
        return "\n========== " + MARK_RESULT_PREFIX
                + " approved=" + approved
                + (reason == null || reason.isBlank() ? "" : " (" + reason + ")")
                + " ==========\n\n";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
```

- [ ] **Step 4: 再跑单测**

Run: `mvn -Dtest=A2aSqlConfirmTextsTest test`

Expected: PASS

- [ ] **Step 5: Commit**（仅当用户要求提交时执行）

```bash
git add src/main/java/org/example/nlp2dsl2sql/a2a/A2aSqlConfirmTexts.java \
  src/test/java/org/example/nlp2dsl2sql/a2a/A2aSqlConfirmTextsTest.java
git commit -m "test: add SQL HITL confirm text helpers"
```

---

### Task 2: VO / 挂起模型 / Registry

**Files:**
- Modify: `src/main/java/org/example/nlp2dsl2sql/models/vo/Nlp2DslAgentRequest.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/models/vo/A2aHostConfirmRequest.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/models/vo/A2aHostConfirmResponse.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostChatContext.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/PendingSqlConfirm.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aSqlConfirmRegistry.java`

- [ ] **Step 1: 扩展请求 VO**

`Nlp2DslAgentRequest` 增加字段：

```java
/** A2A Host HITL 会话 ID（前端生成；缺失时服务端生成） */
private String sessionId;
```

- [ ] **Step 2: 确认入参/出参 VO**

```java
package org.example.nlp2dsl2sql.models.vo;

import lombok.Data;

/** A2A Host SQL 确认请求。 */
@Data
public class A2aHostConfirmRequest {
    /** 会话 ID */
    private String sessionId;
    /** 前端预判（仅日志；服务端以 rawInput 为准） */
    private Boolean approved;
    /** 用户原始输入 */
    private String rawInput;
}
```

```java
package org.example.nlp2dsl2sql.models.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A2A Host SQL 确认响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class A2aHostConfirmResponse {
    private boolean ok;
    private String message;

    public static A2aHostConfirmResponse ok(String message) {
        return new A2aHostConfirmResponse(true, message);
    }

    public static A2aHostConfirmResponse fail(String message) {
        return new A2aHostConfirmResponse(false, message);
    }
}
```

- [ ] **Step 3: ChatContext + Pending + Registry**

`A2aHostChatContext`：

```java
package org.example.nlp2dsl2sql.a2a;

import lombok.Getter;
import reactor.core.publisher.Sinks;

/**
 * 单次 A2A Host SSE 请求上下文（经 RuntimeContext 注入工具）。
 */
@Getter
public class A2aHostChatContext {

    private final String sessionId;
    private final Sinks.Many<String> sseSink;

    public A2aHostChatContext(String sessionId) {
        this.sessionId = sessionId;
        this.sseSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    /**
     * 向 Host SSE 桥推送文本（失败仅打日志，不抛）。
     */
    public void emit(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        sseSink.tryEmitNext(chunk);
    }

    /** SSE 结束时完成 Sink。 */
    public void complete() {
        sseSink.tryEmitComplete();
    }
}
```

`PendingSqlConfirm`：

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 一次 execute_sql ASK 挂起快照。
 */
@Getter
public class PendingSqlConfirm {

    private final String sessionId;
    private final ReActAgent agent;
    private final List<ToolUseBlock> toolCalls;
    private final CompletableFuture<Boolean> decision;
    private final long createdAtMs;

    public PendingSqlConfirm(
            String sessionId,
            ReActAgent agent,
            List<ToolUseBlock> toolCalls) {
        this.sessionId = sessionId;
        this.agent = agent;
        this.toolCalls = toolCalls;
        this.decision = new CompletableFuture<>();
        this.createdAtMs = System.currentTimeMillis();
    }
}
```

`A2aSqlConfirmRegistry`（`@Component`）：

- `put(PendingSqlConfirm)` / `remove(sessionId)` / `get(sessionId)`
- `complete(sessionId, boolean approved)` → `decision.complete`
- 定时或在 get/put 时清理超过 **5 分钟** 的挂起：`decision.complete(false)` 并 remove
- 方法均加注释

- [ ] **Step 4: 编译**

Run: `mvn -DskipTests compile`

Expected: BUILD SUCCESS

---

### Task 3: HITL Agent Factory

**Files:**
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/SqlQueryHitlAgentFactory.java`

- [ ] **Step 1: 实现 Factory**

每次 `create()` 新建 `ReActAgent`（避免多会话共用状态）：

```java
PermissionContextState.Builder perm = PermissionContextState.builder()
        .mode(PermissionMode.DEFAULT);

String[] allowTools = {
        "classify_intent", "retrieve_metadata", "generate_dsl",
        "validate_dsl", "enrich_dsl", "translate_sql", "review_sql"
};
for (String name : allowTools) {
    perm.addAllowRule(name, new PermissionRule(
            name, null, PermissionBehavior.ALLOW, "hitl-policy"));
}
perm.addAskRule("execute_sql", new PermissionRule(
        "execute_sql", null, PermissionBehavior.ASK, "hitl-policy"));

Toolkit toolkit = new Toolkit();
toolkit.registerTool(toolRegistry);

return ReActAgent.builder()
        .name("sql-query-hitl-agent")
        .sysPrompt(Nlp2dsl2sqlAgent.SUPERVISOR_PROMPT)
        .model(model)
        .toolkit(toolkit)
        .permissionContext(perm.build())
        .maxIters(15)
        .build();
```

**不要**修改 `sqlQueryReActAgent` Bean。

- [ ] **Step 2: 编译通过**

Run: `mvn -DskipTests compile`

---

### Task 4: 改造 `call_sql_agent` 为本地 HITL

**Files:**
- Modify: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aRemoteAgentTools.java`

- [ ] **Step 1: 注入依赖并改签名**

构造器增加：`SqlQueryHitlAgentFactory`、`A2aSqlConfirmRegistry`。

`callSqlAgent` 增加可注入参数 `A2aHostChatContext hostCtx`（与 `AgentSessionContext` 相同，由 RuntimeContext 按类型注入）。若 `hostCtx == null`，回退旧 A2A `sqlAgent.call`（兜底）。

- [ ] **Step 2: HITL 执行核心（封装独立私有方法，避免超长）**

伪代码要点：

```java
ReActAgent agent = hitlFactory.create();
AgentSessionContext sqlSession = new AgentSessionContext();
RuntimeContext sqlCtx = RuntimeContext.builder()
        .userId("lsy")
        .sessionId(hostCtx.getSessionId())
        .put(AgentSessionContext.class, sqlSession)
        .build();

StringBuilder finalText = new StringBuilder();
agent.streamEvents(new UserMessage(query.trim()), sqlCtx)
    .doOnNext(event -> handleHitlEvent(event, hostCtx, agent, finalText))
    .blockLast(Duration.ofMillis(clientProperties.getSqlAgent().getTimeoutMs()));

return finalText.length() > 0 ? finalText.toString()
        : "SQL Agent 返回空内容";
```

`handleHitlEvent`：

- `TextBlockDeltaEvent` / `ToolResultTextDeltaEvent` → `hostCtx.emit` + append finalText（工具结果可按现有 Host 风格推送）
- `RequireUserConfirmEvent`：
  1. 从 `toolCalls` 取 `execute_sql` 的 `input.get("sql")`
  2. `hostCtx.emit(A2aSqlConfirmTexts.formatPending(...))`
  3. `registry.put(new PendingSqlConfirm(sessionId, agent, toolCalls))`
  4. `boolean approved = pending.getDecision().get(5, TimeUnit.MINUTES)`（超时 `false`）
  5. `registry.remove(sessionId)`
  6. `hostCtx.emit(formatResult(...))`
  7. 构造 `ConfirmResult` 列表 + `Msg` metadata `Msg.METADATA_CONFIRM_RESULTS`，`agent.call(List.of(resumeMsg), sqlCtx).block(...)`
  8. 将 resume 后继续产生的文本并入（若 `call` 只返回最终 Msg，用 `A2aMsgTexts.extract` append；若需继续流式，可再 `streamEvents`——首版：`call` 拿最终文本即可，同时把提取文本 emit）

注意：在工具线程 `block`/`get` 可以；不要在 Reactor 回调里嵌套 `block`。`doOnNext` 内若需阻塞等待确认，改为在 `streamEvents` 外用同步订阅循环，或 `concatMap`+独立调度；**推荐**封装：

```java
private String runHitlSql(String query, A2aHostChatContext hostCtx) {
    // subscribe on current tool thread with .toIterable() / blockingIterable
    for (AgentEvent event : agent.streamEvents(msg, sqlCtx).toIterable()) {
        if (event instanceof RequireUserConfirmEvent confirm) {
            resumeAfterConfirm(confirm, hostCtx, agent, sqlCtx, finalText);
            continue;
        }
        // map其他事件...
    }
}
```

恢复后若框架在同一次 `streamEvents` 已结束，则对 `agent.call(resumeMsg)` 的返回 Msg 提文本；若仍有后续工具，必要时循环：检测 `PERMISSION_ASKING` 再挂起（首版假设单次 execute_sql 确认）。

- [ ] **Step 3: 编译**

Run: `mvn -DskipTests compile`

---

### Task 5: Service + Controller

**Files:**
- Modify: `src/main/java/org/example/nlp2dsl2sql/service/IA2aHostService.java`
- Modify: `src/main/java/org/example/nlp2dsl2sql/service/impl/A2aHostServiceImpl.java`
- Modify: `src/main/java/org/example/nlp2dsl2sql/controller/A2aHostController.java`

- [ ] **Step 1: 接口**

```java
Flux<String> chat(String sessionId, String question);

A2aHostConfirmResponse confirm(A2aHostConfirmRequest request);
```

- [ ] **Step 2: Service 实现要点**

```java
String sid = (sessionId == null || sessionId.isBlank())
        ? UUID.randomUUID().toString() : sessionId.trim();
A2aHostChatContext hostCtx = new A2aHostChatContext(sid);
RuntimeContext ctx = RuntimeContext.builder()
        .userId("lsy")
        .sessionId(sid)
        .put(A2aHostChatContext.class, hostCtx)
        .build();

Flux<String> agentFlux = a2aHostAgent.streamEvents(new UserMessage(trimmed), ctx)
        .mapNotNull(this::mapEventToSseChunk)
        .doFinally(s -> hostCtx.complete());

Flux<String> bridgeFlux = hostCtx.getSseSink().asFlux();

Flux<String> head = Flux.just("sessionId: " + sid + "\n");
return Flux.merge(head, agentFlux, bridgeFlux)
        .doOnError(...)
        .onErrorResume(...);
```

`confirm`：

```java
if (request.getSessionId() == null || request.getSessionId().isBlank()) {
    return A2aHostConfirmResponse.fail("sessionId 不能为空");
}
boolean approved = A2aSqlConfirmTexts.isApproved(request.getRawInput());
boolean ok = registry.complete(request.getSessionId().trim(), approved);
if (!ok) {
    return A2aHostConfirmResponse.fail("无待确认 SQL 或已过期");
}
return A2aHostConfirmResponse.ok(approved ? "已批准执行" : "已取消执行");
```

- [ ] **Step 3: Controller**

```java
@GetMapping(value = "/a2aHost", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> a2aHost(Nlp2DslAgentRequest request) {
    return a2aHostService.chat(request.getSessionId(), request.getQuestion());
}

@PostMapping(value = "/a2aHost/confirm",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
public A2aHostConfirmResponse confirm(@RequestBody A2aHostConfirmRequest request) {
    return a2aHostService.confirm(request);
}
```

- [ ] **Step 4: 编译**

Run: `mvn -DskipTests compile`

---

### Task 6: 前端 A2A Host 确认 UI

**Files:**
- Modify: `src/main/resources/static/nlp2dsl2sqlV2.html`

- [ ] **Step 1: 状态变量**

```javascript
let a2aSessionId = null;
let awaitingSqlConfirm = false;
```

切换到 `a2aHost` 或首次发送时：`a2aSessionId = crypto.randomUUID()`（无则简易 UUID）。

- [ ] **Step 2: SSE URL**

```javascript
if (currentMode === 'a2aHost') {
    const sid = encodeURIComponent(a2aSessionId || '');
    return `${API_BASE_URL}/aiChat/a2aHost?sessionId=${sid}`;
}
```

发送时仍追加 `&question=...`。

- [ ] **Step 3: 流式检测待确认**

在拼接 `fullText` 后若包含 `[SQL确认待审批]` 且尚未展示确认区：`showSqlConfirmPanel(bubble, fullText)`，设 `awaitingSqlConfirm = true`。

确认区：只读 SQL（从 `sql:` 到下一 `==========`）+ input + 按钮「提交确认」。

- [ ] **Step 4: 提交**

```javascript
async function submitSqlConfirm(rawInput) {
    const resp = await fetch(`${API_BASE_URL}/aiChat/a2aHost/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            sessionId: a2aSessionId,
            approved: null,
            rawInput: rawInput
        })
    });
    // 成功后 awaitingSqlConfirm=false，收起面板，继续读 SSE
}
```

确认态下主输入框 Enter 走 `submitSqlConfirm`，不发新问题。

- [ ] **Step 5: 手动点选 A2A Host，浏览器验证标记文案出现**（需后端已启动）

---

### Task 7: 联调验收

- [ ] **Step 1: 启动应用** `mvn spring-boot:run`（或现有启动方式）
- [ ] **Step 2: 单 SQL** — 输入数据问题 → 见待确认 → 输入 `确认` → 有查询结论
- [ ] **Step 3: 输入 `yes` 同样通过；输入 `no` 取消且不执行
- [ ] **Step 4: 复合 SQL+SOP，拒绝 SQL 后 SOP 仍可汇总
- [ ] **Step 5: 其它模式无回归；A2A `sqlQueryReActAgent` 仍无 HITL

---

## Spec coverage checklist

| Spec 项 | Task |
|---------|------|
| 仅 A2A Host | Task 4/5（其它入口不改） |
| Permission ASK execute_sql | Task 3 |
| 本地 streamEvents 非 A2A SQL | Task 4 |
| SSE 确认标记 | Task 1 + 4 |
| POST confirm + rawInput 服务端判定 | Task 2 + 5 |
| 拒绝取消子查询 | Task 4/5 |
| 5 分钟超时 | Task 2 Registry |
| 前端 yes/确认 | Task 6 |
| 不改 sqlQueryReActAgent | Task 3 明确 |
| 多 SQL 串行 | 自然由 Host 串行 tool call + 单 session 挂起 |

---

## Execution handoff

Plan complete. 推荐本会话 **Inline Execution** 按 Task 1→7 推进（仓库暂无现成测试体系，但仍保留 Task 1 单测）。
