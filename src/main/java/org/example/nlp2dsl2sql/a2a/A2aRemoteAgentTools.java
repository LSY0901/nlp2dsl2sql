package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.A2aClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Host 可调用的远程/本地 Agent 工具。
 * <p>
 * SQL：A2A Host 场景走本地 HITL；无 Host 上下文时回退 A2A。
 * SOP：始终 A2A。
 */
@Slf4j
@Component
public class A2aRemoteAgentTools {

    private final A2aAgent sqlAgent;
    private final A2aAgent sopAgent;
    private final A2aClientProperties clientProperties;
    private final A2aSqlHitlRunner sqlHitlRunner;

    /**
     * @param sqlAgent         本机 SQL A2A 客户端（兜底）
     * @param sopAgent         远端 SOP A2A 客户端
     * @param clientProperties A2A 超时等连接配置
     * @param sqlHitlRunner    Host SQL HITL 执行器
     */
    public A2aRemoteAgentTools(
            @Qualifier("sqlQueryA2aAgent") A2aAgent sqlAgent,
            @Qualifier("sopDocA2aAgent") A2aAgent sopAgent,
            A2aClientProperties clientProperties,
            A2aSqlHitlRunner sqlHitlRunner) {
        this.sqlAgent = sqlAgent;
        this.sopAgent = sopAgent;
        this.clientProperties = clientProperties;
        this.sqlHitlRunner = sqlHitlRunner;
    }

    /**
     * 调用 SQL 查询 Agent（Host 下为本地 HITL）。
     *
     * @param query   可独立回答的数据子问题
     * @param hostCtx Host SSE 上下文（RuntimeContext 注入；可为 null）
     * @return 回答文本或错误说明
     */
    @Tool(name = "call_sql_agent", description = """
            将数据查询类子问题发给 SQL Agent。
            适用于指标、对比、明细、最高分是谁等需要查库的问题。
            执行 SQL 前可能需要用户确认。
            """)
    public String callSqlAgent(
            @ToolParam(name = "query", description = "子问题原文") String query,
            A2aHostChatContext hostCtx) {
        if (query == null || query.isBlank()) {
            return "SQL Agent 调用失败: query 为空";
        }
        long timeoutMs = clientProperties.getSqlAgent().getTimeoutMs();
        if (hostCtx != null) {
            log.info("[HITL] 本地 SQL Agent, sessionId={}, query={}",
                    hostCtx.getSessionId(), query);
            return sqlHitlRunner.run(query.trim(), hostCtx, timeoutMs);
        }
        return callRemote("SQL", sqlAgent, query, timeoutMs);
    }

    /**
     * 调用 SOP 文档 Agent。
     *
     * @param query 可独立回答的规范子问题
     * @return 远端回答文本或错误说明
     */
    @Tool(name = "call_sop_agent", description = """
            将规范/流程/奖励/业务说明/领域概念类子问题发给 SOP Agent（A2A）。
            适用于操作规范、奖励政策、SOP 文档问答，以及「XX是干什么的」等业务概念说明。
            此类问题必须调用本工具，禁止 Host 自行回答。
            """)
    public String callSopAgent(
            @ToolParam(name = "query", description = "子问题原文") String query) {
        long timeoutMs = clientProperties.getSopAgent().getTimeoutMs();
        return callRemote("SOP", sopAgent, query, timeoutMs);
    }

    /**
     * 统一 A2A 调用与错误包装。
     *
     * @param label     日志标签
     * @param agent     A2aAgent
     * @param query     子问题
     * @param timeoutMs 阻塞等待超时毫秒
     * @return 文本结果
     */
    private String callRemote(
            String label, A2aAgent agent, String query, long timeoutMs) {
        if (query == null || query.isBlank()) {
            return label + " Agent 调用失败: query 为空";
        }
        try {
            log.info("[A2A] 调用 {} Agent, query={}", label, query);
            Duration timeout = Duration.ofMillis(timeoutMs);
            Msg msg = agent.call(new UserMessage(query.trim())).block(timeout);
            String text = A2aMsgTexts.extract(msg);
            if (text.isBlank()) {
                return label + " Agent 返回空内容";
            }
            return text;
        } catch (Exception e) {
            log.warn("[A2A] {} Agent 调用失败: {}", label, e.getMessage());
            return label + " Agent 调用失败: " + e.getMessage();
        }
    }
}
