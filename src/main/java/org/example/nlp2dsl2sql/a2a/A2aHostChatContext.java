package org.example.nlp2dsl2sql.a2a;

import lombok.Getter;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次 A2A Host SSE 请求上下文（经 RuntimeContext 注入工具）。
 * <p>
 * 同时承载本轮对话的子工具调用轨迹（SQL / SOP / 最终回答），
 * 供 Host 在 {@code ON_COMPLETE} 时按「一问一答一轮」汇总归档至 Redis {@code turns} 与 PG {@code memory_session_archive}。
 */
@Getter
public class A2aHostChatContext {

    private final String sessionId;
    private final String userId;
    private final Sinks.Many<String> sseSink;

    private final AtomicBoolean sqlCalled = new AtomicBoolean(false);
    private final AtomicBoolean sopCalled = new AtomicBoolean(false);
    private volatile String lastEffectiveQuery;
    private volatile SemanticQueryDSL lastDsl;
    private final StringBuffer hostAnswerBuffer = new StringBuffer();

    /**
     * 构造 Host 聊天上下文。
     *
     * @param sessionId 会话 ID
     */
    public A2aHostChatContext(String sessionId) {
        this(sessionId, null);
    }

    /**
     * 构造 Host 聊天上下文。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     */
    public A2aHostChatContext(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.sseSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    /**
     * 向 Host SSE 桥推送文本（失败静默，不抛异常）。
     *
     * @param chunk 文本增量
     */
    public void emit(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        sseSink.tryEmitNext(chunk);
    }

    /**
     * SSE 结束时完成 Sink。
     */
    public void complete() {
        sseSink.tryEmitComplete();
    }

    /**
     * 记录本轮调用了 SQL 子 Agent（及产出的改写问句与 DSL）。
     *
     * @param effectiveQuery 追问改写后的生效子问题
     * @param dsl            生成的语义 DSL（可为 null）
     */
    public void recordSqlTurn(String effectiveQuery, SemanticQueryDSL dsl) {
        sqlCalled.set(true);
        if (effectiveQuery != null && !effectiveQuery.isBlank()) {
            this.lastEffectiveQuery = effectiveQuery.trim();
        }
        if (dsl != null) {
            this.lastDsl = dsl;
        }
    }

    /**
     * 记录本轮调用了远端 SOP 子 Agent。
     */
    public void recordSopCall() {
        sopCalled.set(true);
    }

    /**
     * 累加 Host 最终自然语言回答增量。
     *
     * @param delta 文本增量
     */
    public void appendHostAnswer(String delta) {
        if (delta != null && !delta.isEmpty()) {
            hostAnswerBuffer.append(delta);
        }
    }

    /**
     * 获取本轮 Host 完整自然语言回答。
     */
    public String getHostAnswer() {
        return hostAnswerBuffer.toString().trim();
    }

    /**
     * 获取本轮最近一次改写后的 SQL 子问题；若未改写则返回默认原问句。
     */
    public String getLastEffectiveQueryOrDefault(String defaultQuery) {
        return (lastEffectiveQuery != null && !lastEffectiveQuery.isBlank())
                ? lastEffectiveQuery
                : defaultQuery;
    }

    /**
     * 根据本轮子 Agent 调用轨迹计算轮次类型：
     * <ul>
     *   <li>同时调用 SQL 与 SOP：{@code MIXED}</li>
     *   <li>仅调用 SQL：{@code SQL}</li>
     *   <li>仅调用 SOP：{@code SOP}</li>
     *   <li>未调用工具：{@code CHITCHAT}</li>
     * </ul>
     */
    public String resolveTurnType() {
        boolean sql = sqlCalled.get();
        boolean sop = sopCalled.get();
        if (sql && sop) {
            return "MIXED";
        }
        if (sql) {
            return "SQL";
        }
        if (sop) {
            return "SOP";
        }
        return "CHITCHAT";
    }
}
