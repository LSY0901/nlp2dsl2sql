# AgentScope A2A Host Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在本项目按 AgentScope 官方 A2A 写法落地 Host：拆解复合问题，经 `A2aAgent.call` 分别调用本机 SQL A2A Server 与远端 SOP A2A Server，汇总流式回答。

**Architecture:** 引入 `agentscope-a2a-spring-boot-starter`；本机用 `ReActAgent` + 现有问数 Toolkit 暴露为 A2A Server；Host 用独立 `HarnessAgent`，Toolkit 仅含 `call_sql_agent` / `call_sop_agent`（内部 `A2aAgent.call`）；新增 `GET /aiChat/a2aHost` SSE。禁止 WebClient 调用 `/chat/stream`。

**Tech Stack:** Java 21、Spring Boot 3.5、AgentScope 2.0.0（Harness + A2A Client/Server）、Reactor `Flux` SSE

**Spec:** `docs/superpowers/specs/2026-07-27-a2a-agentscope-host-design.md`

**Out of repo:** 9002 SOP 项目改造为 A2A Server（本计划 Task 8 仅列联调清单，不在本仓库改代码）

---

## File Structure

```
pom.xml                                          # 增加 a2a starter + test
src/main/resources/application.yaml              # agentscope.a2a + a2a.* 配置

src/main/java/org/example/nlp2dsl2sql/
├── config/
│   └── A2aClientProperties.java                 # sql/sop base-url、card-path、timeout
├── a2a/
│   ├── A2aMsgTexts.java                         # 从 Msg 提取纯文本
│   ├── A2aClientConfig.java                     # 两个 A2aAgent Bean
│   ├── A2aRemoteAgentTools.java                 # @Tool call_sql_agent / call_sop_agent
│   ├── A2aHostPrompt.java                       # Host sysPrompt 常量
│   ├── A2aHostAgentConfig.java                  # Host HarnessAgent Bean
│   └── SqlQueryA2aServerAgentConfig.java        # 供 A2A Server 暴露的 ReActAgent
├── controller/
│   └── A2aHostController.java
├── service/
│   ├── IA2aHostService.java
│   └── impl/A2aHostServiceImpl.java

src/test/java/org/example/nlp2dsl2sql/a2a/
├── A2aMsgTextsTest.java
└── A2aRemoteAgentToolsTest.java
```

---

### Task 1: 依赖与配置骨架

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/java/org/example/nlp2dsl2sql/config/A2aClientProperties.java`

- [ ] **Step 1: 在 `pom.xml` 的 `</dependencies>` 前增加依赖**

```xml
        <!-- AgentScope A2A（含 client + server 自动配置） -->
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-a2a-spring-boot-starter</artifactId>
            <version>${agentscope.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 合并写入 `application.yaml`（不要重复顶级 `agentscope:`）**

在现有 `agentscope.openai` 同级增加 `a2a.server`，并新增顶级 `a2a:`：

```yaml
agentscope:
  model:
    provider: openai
  openai:
    api-key: sk-...
    base-url: https://api.deepseek.com
    model-name: deepseek-v4-pro
  a2a:
    server:
      enabled: true
      card:
        name: sql-query-agent
        description: 自然语言业务数据查询（NLP→DSL→SQL）

a2a:
  sql-agent:
    base-url: http://127.0.0.1:8079
    agent-card-path: /.well-known/agent-card.json
  sop-agent:
    base-url: http://127.0.0.1:9002
    agent-card-path: /.well-known/agent-card.json
    timeout-ms: 60000
```

- [ ] **Step 3: 创建配置属性类**

路径：`src/main/java/org/example/nlp2dsl2sql/config/A2aClientProperties.java`

```java
package org.example.nlp2dsl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A2A 客户端连接配置（SQL / SOP 远端 Agent）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "a2a")
public class A2aClientProperties {

    /** 本机 SQL A2A Server（回环调用） */
    private AgentEndpoint sqlAgent = new AgentEndpoint();

    /** 远端 SOP A2A Server */
    private AgentEndpoint sopAgent = new AgentEndpoint();

    /**
     * 单个远端 Agent 连接信息。
     */
    @Data
    public static class AgentEndpoint {
        /** 服务根地址，如 http://127.0.0.1:9002 */
        private String baseUrl = "http://127.0.0.1:9002";
        /** AgentCard 路径 */
        private String agentCardPath = "/.well-known/agent-card.json";
        /** 调用超时毫秒 */
        private long timeoutMs = 60000L;
    }
}
```

- [ ] **Step 4: 编译验证依赖可解析**

Run:

```bash
./mvnw -q -DskipTests compile
```

Expected: BUILD SUCCESS。若 `agentscope-a2a-spring-boot-starter` 与 Boot 3.5 冲突，改为分别引入：

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-a2a-client</artifactId>
  <version>${agentscope.version}</version>
</dependency>
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-a2a-server</artifactId>
  <version>${agentscope.version}</version>
</dependency>
```

并在 Task 4 手写 `AgentScopeA2aServer` + Controller（以官方文档为准）。

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yaml \
  src/main/java/org/example/nlp2dsl2sql/config/A2aClientProperties.java
git commit -m "$(cat <<'EOF'
chore: add AgentScope A2A dependencies and client properties

EOF
)"
```

---

### Task 2: Msg 文本提取工具（可单测）

**Files:**
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aMsgTexts.java`
- Create: `src/test/java/org/example/nlp2dsl2sql/a2a/A2aMsgTextsTest.java`

- [ ] **Step 1: 写失败测试**

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aMsgTextsTest {

    @Test
    void extract_returnsEmpty_whenMsgNull() {
        assertEquals("", A2aMsgTexts.extract(null));
    }

    @Test
    void extract_joinsTextBlocks() {
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("hello")
                .build();
        String text = A2aMsgTexts.extract(msg);
        assertTrue(text.contains("hello"));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./mvnw -q -Dtest=A2aMsgTextsTest test`  
Expected: 编译失败或找不到 `A2aMsgTexts`

- [ ] **Step 3: 实现 `A2aMsgTexts`**

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;

/**
 * 从 AgentScope Msg 中提取纯文本，供 A2A tool 返回给 Host。
 */
public final class A2aMsgTexts {

    private A2aMsgTexts() {
    }

    /**
     * 提取消息中全部 TextBlock 内容并拼接。
     *
     * @param msg A2A / Agent 返回消息，可为 null
     * @return 文本；空则返回空串
     */
    public static String extract(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                String t = tb.getText();
                if (t != null && !t.isEmpty()) {
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }
}
```

若 `Msg.getContent()` API 不一致，按本仓库 `IntentTool` 遍历 content 的写法对齐。

- [ ] **Step 4: 再跑测试**

Run: `./mvnw -q -Dtest=A2aMsgTextsTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/nlp2dsl2sql/a2a/A2aMsgTexts.java \
  src/test/java/org/example/nlp2dsl2sql/a2a/A2aMsgTextsTest.java
git commit -m "$(cat <<'EOF'
feat: add A2aMsgTexts helper for A2A Msg extraction

EOF
)"
```

---

### Task 3: A2aAgent Bean + Remote Tools

**Files:**
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aClientConfig.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aRemoteAgentTools.java`
- Create: `src/test/java/org/example/nlp2dsl2sql/a2a/A2aRemoteAgentToolsTest.java`

- [ ] **Step 1: 写 Tools 单测**

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aRemoteAgentToolsTest {

    @Mock
    private A2aAgent sqlAgent;

    @Mock
    private A2aAgent sopAgent;

    @Test
    void callSqlAgent_returnsRemoteText() {
        Msg reply = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("最高分是张三")
                .build();
        when(sqlAgent.call(any(UserMessage.class))).thenReturn(Mono.just(reply));

        A2aRemoteAgentTools tools = new A2aRemoteAgentTools(sqlAgent, sopAgent);
        String out = tools.callSqlAgent("六年级最高分是谁？");
        assertTrue(out.contains("张三"));
    }

    @Test
    void callSopAgent_returnsErrorText_whenCallFails() {
        when(sopAgent.call(any(UserMessage.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        A2aRemoteAgentTools tools = new A2aRemoteAgentTools(sqlAgent, sopAgent);
        String out = tools.callSopAgent("最高分奖励是什么？");
        assertTrue(out.contains("失败") || out.contains("connection refused"));
    }
}
```

若 `A2aAgent.call` 签名不是 `Mono<Msg>`，按 2.0.0 源码调整 mock。

- [ ] **Step 2: 跑测确认失败**

Run: `./mvnw -q -Dtest=A2aRemoteAgentToolsTest test`  
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 `A2aClientConfig`**

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;
import org.example.nlp2dsl2sql.config.A2aClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 装配两个远端 A2aAgent（SQL 回环 + SOP）。
 */
@Configuration
public class A2aClientConfig {

    /**
     * 本机 SQL A2A Agent 客户端。
     *
     * @param props 连接配置
     * @return A2aAgent
     */
    @Bean(name = "sqlQueryA2aAgent")
    public A2aAgent sqlQueryA2aAgent(A2aClientProperties props) {
        A2aClientProperties.AgentEndpoint ep = props.getSqlAgent();
        return A2aAgent.builder()
                .name("sql-query-agent")
                .agentCardResolver(new WellKnownAgentCardResolver(
                        ep.getBaseUrl(),
                        ep.getAgentCardPath(),
                        Map.of()))
                .build();
    }

    /**
     * 远端 SOP A2A Agent 客户端。
     *
     * @param props 连接配置
     * @return A2aAgent
     */
    @Bean(name = "sopDocA2aAgent")
    public A2aAgent sopDocA2aAgent(A2aClientProperties props) {
        A2aClientProperties.AgentEndpoint ep = props.getSopAgent();
        return A2aAgent.builder()
                .name("sop-doc-agent")
                .agentCardResolver(new WellKnownAgentCardResolver(
                        ep.getBaseUrl(),
                        ep.getAgentCardPath(),
                        Map.of()))
                .build();
    }
}
```

- [ ] **Step 4: 实现 `A2aRemoteAgentTools`**

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Host 可调用的 A2A 远程 Agent 工具（必须经 A2aAgent.call）。
 */
@Slf4j
@Component
public class A2aRemoteAgentTools {

    private final A2aAgent sqlAgent;
    private final A2aAgent sopAgent;

    /**
     * @param sqlAgent 本机 SQL A2A 客户端
     * @param sopAgent 远端 SOP A2A 客户端
     */
    public A2aRemoteAgentTools(
            @Qualifier("sqlQueryA2aAgent") A2aAgent sqlAgent,
            @Qualifier("sopDocA2aAgent") A2aAgent sopAgent) {
        this.sqlAgent = sqlAgent;
        this.sopAgent = sopAgent;
    }

    /**
     * 调用 SQL 查询 Agent。
     *
     * @param query 可独立回答的数据子问题
     * @return 远端回答文本或错误说明
     */
    @Tool(name = "call_sql_agent", description = """
            将数据查询类子问题发给 SQL Agent（A2A）。
            适用于指标、对比、明细、最高分是谁等需要查库的问题。
            """)
    public String callSqlAgent(
            @ToolParam(name = "query", description = "子问题原文") String query) {
        return callRemote("SQL", sqlAgent, query);
    }

    /**
     * 调用 SOP 文档 Agent。
     *
     * @param query 可独立回答的规范子问题
     * @return 远端回答文本或错误说明
     */
    @Tool(name = "call_sop_agent", description = """
            将规范/流程/奖励类子问题发给 SOP Agent（A2A）。
            适用于操作规范、奖励政策、SOP 文档问答。
            """)
    public String callSopAgent(
            @ToolParam(name = "query", description = "子问题原文") String query) {
        return callRemote("SOP", sopAgent, query);
    }

    /**
     * 统一 A2A 调用与错误包装。
     *
     * @param label 日志标签
     * @param agent A2aAgent
     * @param query 子问题
     * @return 文本结果
     */
    private String callRemote(String label, A2aAgent agent, String query) {
        if (query == null || query.isBlank()) {
            return label + " Agent 调用失败: query 为空";
        }
        try {
            log.info("[A2A] 调用 {} Agent, query={}", label, query);
            Msg msg = agent.call(new UserMessage(query.trim()))
                    .block(Duration.ofSeconds(60));
            String text = A2aMsgTexts.extract(msg);
            if (text == null || text.isBlank()) {
                return label + " Agent 返回空内容";
            }
            return text;
        } catch (Exception e) {
            log.warn("[A2A] {} Agent 调用失败: {}", label, e.getMessage());
            return label + " Agent 调用失败: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 5: 跑单测**

Run: `./mvnw -q -Dtest=A2aRemoteAgentToolsTest,A2aMsgTextsTest test`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/example/nlp2dsl2sql/a2a/A2aClientConfig.java \
  src/main/java/org/example/nlp2dsl2sql/a2a/A2aRemoteAgentTools.java \
  src/test/java/org/example/nlp2dsl2sql/a2a/A2aRemoteAgentToolsTest.java
git commit -m "$(cat <<'EOF'
feat: wire A2aAgent clients and Host remote tools

EOF
)"
```

---

### Task 4: SQL A2A Server 用 ReActAgent

**Files:**
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/SqlQueryA2aServerAgentConfig.java`

说明：starter 通常需要 `ReActAgent` / `ReActAgent.Builder` Bean。现有问数是 `HarnessAgent`，需单独提供 A2A 用的 `ReActAgent`，Toolkit 复用 `AgentToolRegistry`。

- [ ] **Step 1: 查阅 `AgentscopeA2aAutoConfiguration` 所需 Bean 类型**

- [ ] **Step 2: 创建 SQL Server Agent 配置**

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.agent.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.example.nlp2dsl2sql.agent.Nlp2dsl2sqlAgent;
import org.example.nlp2dsl2sql.tools.AgentToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 供 A2A Server 暴露的 SQL 查询 ReActAgent（复用问数 Toolkit）。
 */
@Configuration
public class SqlQueryA2aServerAgentConfig {

    /**
     * A2A 对外的 sql-query-agent。
     *
     * @param model        LLM
     * @param toolRegistry 现有问数工具
     * @return ReActAgent
     */
    @Bean
    public ReActAgent sqlQueryReActAgent(
            OpenAIChatModel model,
            AgentToolRegistry toolRegistry) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(toolRegistry);
        return ReActAgent.builder()
                .name("sql-query-agent")
                .sysPrompt(Nlp2dsl2sqlAgent.SUPERVISOR_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .maxIters(15)
                .build();
    }
}
```

包名以依赖为准。若 starter 要 `ReActAgent.Builder`，改为返回 Builder Bean。

- [ ] **Step 3: 启动后检查 AgentCard**

```text
GET http://127.0.0.1:8079/.well-known/agent-card.json
```

Expected: 200。若 404：本 Task 内按官方文档手写 `AgentScopeA2aServer` + Controller，不留缺口。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/example/nlp2dsl2sql/a2a/SqlQueryA2aServerAgentConfig.java
git commit -m "$(cat <<'EOF'
feat: expose SQL ReActAgent as AgentScope A2A server agent

EOF
)"
```

---

### Task 5: Host HarnessAgent

**Files:**
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostPrompt.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostAgentConfig.java`

- [ ] **Step 1: Host Prompt**

```java
package org.example.nlp2dsl2sql.a2a;

/**
 * A2A Host Agent 系统提示词。
 */
public final class A2aHostPrompt {

    private A2aHostPrompt() {
    }

    public static final String SYSTEM = """
            你是 A2A 协作 Host Agent。你不直接查库或读 SOP 文档，只能通过工具调用专业 Agent。

            ## 可用工具
            - call_sql_agent(query): 数据查询（指标/对比/明细/最高分是谁等）
            - call_sop_agent(query): 规范文档（SOP/流程/奖励政策等）

            ## 工作方式
            1. 将用户问题拆成可独立回答的子问题（最多 5 个）
            2. 数据子问题只调用 call_sql_agent；规范子问题只调用 call_sop_agent
            3. 复合问题示例：「六年级最高分是谁？最高分奖励是什么」
               → 先 call_sql_agent("六年级最高分是谁？")
               → 再 call_sop_agent("最高分奖励是什么？")
            4. 基于全部工具结果，用中文给出完整最终回答
            5. 某一侧失败时说明失败项，仍尽量回答成功侧；禁止编造数据
            6. 纯闲聊可直接回答，不调用工具
            7. 不要调用文件/Shell/记忆等无关内置工具
            """;
}
```

- [ ] **Step 2: 装配 Host Bean**

```java
package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * A2A Host HarnessAgent 配置。
 */
@Configuration
public class A2aHostAgentConfig {

    /**
     * Host Agent：只注册 A2A 远程工具。
     *
     * @param model LLM
     * @param tools A2A 工具
     * @return HarnessAgent
     */
    @Bean(name = "a2aHostAgent")
    public HarnessAgent a2aHostAgent(
            OpenAIChatModel model,
            A2aRemoteAgentTools tools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        return HarnessAgent.builder()
                .name("a2aHostAgent")
                .sysPrompt(A2aHostPrompt.SYSTEM)
                .model(model)
                .toolkit(toolkit)
                .maxIters(10)
                .workspace(Paths.get(".agentscope/workspace-a2a-host"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
```

- [ ] **Step 3: 编译**

Run: `./mvnw -q -DskipTests compile`  
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostPrompt.java \
  src/main/java/org/example/nlp2dsl2sql/a2a/A2aHostAgentConfig.java
git commit -m "$(cat <<'EOF'
feat: add A2A Host HarnessAgent with remote agent tools

EOF
)"
```

---

### Task 6: Host Service + Controller SSE

**Files:**
- Create: `src/main/java/org/example/nlp2dsl2sql/service/IA2aHostService.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/service/impl/A2aHostServiceImpl.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/controller/A2aHostController.java`

- [ ] **Step 1: Service 接口**

```java
package org.example.nlp2dsl2sql.service;

import reactor.core.publisher.Flux;

/**
 * A2A Host 流式编排服务。
 */
public interface IA2aHostService {

    /**
     * 启动 Host Agent，流式返回 SSE 文本。
     *
     * @param question 用户问题
     * @return 文本流
     */
    Flux<String> chat(String question);
}
```

- [ ] **Step 2: Service 实现（对齐 `Nlp2dsl2sqlAgentServiceImpl`）**

```java
package org.example.nlp2dsl2sql.service.impl;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.service.IA2aHostService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * A2A Host SSE 服务。
 */
@Slf4j
@Service
public class A2aHostServiceImpl implements IA2aHostService {

    private final HarnessAgent a2aHostAgent;

    /**
     * @param a2aHostAgent Host Agent
     */
    public A2aHostServiceImpl(
            @Qualifier("a2aHostAgent") HarnessAgent a2aHostAgent) {
        this.a2aHostAgent = a2aHostAgent;
    }

    @Override
    public Flux<String> chat(String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }
        String trimmed = question.trim();
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("a2a-host")
                .sessionId("a2a-" + System.currentTimeMillis())
                .build();
        log.info("━━━━━━━ A2A Host 启动 ━━━━━━━ question={}", trimmed);
        return a2aHostAgent
                .streamEvents(new UserMessage(trimmed), ctx)
                .mapNotNull(this::mapEventToSseChunk)
                .doOnComplete(() -> log.info("━━━━━━━ A2A Host 完成 ━━━━━━━"))
                .doOnError(e -> log.error("A2A Host 异常", e))
                .onErrorResume(e -> Flux.just("错误: " + e.getMessage()));
    }

    /**
     * 将 AgentEvent 映射为 SSE 文本。
     *
     * @param event 事件
     * @return 文本或 null
     */
    private String mapEventToSseChunk(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta) {
            String text = delta.getDelta();
            return (text == null || text.isEmpty()) ? null : text;
        }
        if (event instanceof ToolCallStartEvent toolCall) {
            return "\n\n========== [A2A工具开始] "
                    + toolCall.getToolCallName() + " ==========\n";
        }
        if (event instanceof ToolResultTextDeltaEvent toolResult) {
            String text = toolResult.getDelta();
            return (text == null || text.isEmpty()) ? null : text;
        }
        if (event instanceof ToolResultEndEvent toolEnd) {
            return "\n========== [A2A工具结束] "
                    + toolEnd.getToolCallName()
                    + " (" + toolEnd.getState() + ") ==========\n\n";
        }
        return null;
    }
}
```

- [ ] **Step 3: Controller**

```java
package org.example.nlp2dsl2sql.controller;

import org.example.nlp2dsl2sql.models.vo.Nlp2DslAgentRequest;
import org.example.nlp2dsl2sql.service.IA2aHostService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AgentScope A2A Host 入口。
 */
@RestController
@RequestMapping("/aiChat")
public class A2aHostController {

    private final IA2aHostService a2aHostService;

    /**
     * @param a2aHostService Host 服务
     */
    public A2aHostController(IA2aHostService a2aHostService) {
        this.a2aHostService = a2aHostService;
    }

    /**
     * A2A Host 流式对话。
     *
     * @param request 含 question
     * @return SSE 文本流
     */
    @GetMapping(value = "/a2aHost", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> a2aHost(Nlp2DslAgentRequest request) {
        return a2aHostService.chat(request.getQuestion());
    }
}
```

- [ ] **Step 4: 编译 + 单测**

```bash
./mvnw -q test
./mvnw -q -DskipTests package
```

Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/nlp2dsl2sql/service/IA2aHostService.java \
  src/main/java/org/example/nlp2dsl2sql/service/impl/A2aHostServiceImpl.java \
  src/main/java/org/example/nlp2dsl2sql/controller/A2aHostController.java
git commit -m "$(cat <<'EOF'
feat: add /aiChat/a2aHost SSE endpoint for A2A Host

EOF
)"
```

---

### Task 7: 本机联调（SQL 回环，可不依赖 9002）

- [ ] **Step 1: 启动应用（8079）**

- [ ] **Step 2: 验证 AgentCard**

```bash
curl -s http://127.0.0.1:8079/.well-known/agent-card.json
```

Expected: JSON 含 sql-query-agent 相关字段

- [ ] **Step 3: 纯 SQL**

```bash
curl -N "http://127.0.0.1:8079/aiChat/a2aHost?question=三年级数学平均分是多少"
```

Expected: 出现 `[A2A工具开始] call_sql_agent`

- [ ] **Step 4: 复合问（SOP 未就绪也可测降级）**

```bash
curl -N "http://127.0.0.1:8079/aiChat/a2aHost?question=六年级最高分是谁？最高分奖励是什么"
```

Expected: 有 `call_sql_agent`；`call_sop_agent` 可能失败文案；最终尽量覆盖 SQL 侧

- [ ] **Step 5: 确认未调用 `/chat/stream`**

日志不应出现对 `http://localhost:9002/chat/stream` 的请求。

- [ ] **Step 6: 设计文档状态改为已批准**

- [ ] **Step 7: Commit 文档（如有）**

```bash
git add docs/superpowers/specs/2026-07-27-a2a-agentscope-host-design.md
git commit -m "$(cat <<'EOF'
docs: mark A2A AgentScope host design approved

EOF
)"
```

---

### Task 8: 9002 SOP A2A 联调清单（外仓）

> **说明：** 下列项为外仓（9002 SOP 项目）联调验收清单；本仓仅交付文档，不在此仓库实现 SOP A2A Server。  
> **清单文档：** `docs/superpowers/specs/2026-07-27-a2a-sop-agent-checklist.md`

- [x] 暴露 `GET /.well-known/agent-card.json`（清单文档已交付，待外仓实现）
- [x] 可被 Java `A2aAgent.call(UserMessage)` 调用（清单文档已交付，待外仓实现）
- [x] `a2a.sop-agent.base-url=http://127.0.0.1:9002` 连通（清单文档已交付，待外仓联调）
- [x] 复合问同时成功 `call_sql_agent` + `call_sop_agent`（清单文档已交付，待外仓联调）
- [x] 最终回答同时含最高分与奖励说明（清单文档已交付，待外仓联调）
- [x] 旧 `/chat/stream` 可保留但 Host 不依赖（清单文档已交付，本仓 Host 已不依赖）

---

## Spec Coverage Checklist

| Spec 项 | Task |
|---------|------|
| A2A Client `A2aAgent` + WellKnown | Task 3 |
| A2A Server 暴露 SQL Agent | Task 4 |
| Host Harness + tools | Task 5 |
| `/aiChat/a2aHost` SSE | Task 6 |
| 配置 yaml | Task 1 |
| 禁止 `/chat/stream` | Task 3/7 |
| 复合拆解示例 | Task 5/7/8 |
| 9002 改造 | Task 8（外仓） |
| 旧端点不改 | 全任务未改旧 Controller |

## Placeholder Scan

无 TBD；starter 冲突有降级路径。

## Type Consistency

- Beans: `sqlQueryA2aAgent` / `sopDocA2aAgent` / `a2aHostAgent` / `sqlQueryReActAgent`
- Tools: `call_sql_agent` / `call_sop_agent`
- Endpoint: `/aiChat/a2aHost`
- Props: `a2a.sql-agent` / `a2a.sop-agent`
