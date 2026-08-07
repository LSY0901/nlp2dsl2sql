package org.example.nlp2dsl2sql.a2a.trace;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.A2aHostTraceProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存 trace 记录器：按 sessionId 记录 A2A Host 请求全链路。
 * <p>
 * 线程安全，按 {@code maxRecords} 与 {@code ttlMs} 淘汰。
 */
@Slf4j
@Component
public class HostTraceRecorder {

    private final int maxRecords;
    private final long ttlMs;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, HostTraceRecord> records = new ConcurrentHashMap<>();

    /**
     * @param properties trace 配置
     */
    public HostTraceRecorder(A2aHostTraceProperties properties) {
        this.maxRecords = Math.max(properties.getMaxRecords(), 1);
        this.ttlMs = Math.max(properties.getTtlMs(), 0);
    }

    /**
     * 开启一条新 trace。
     *
     * @param sessionId 会话 ID
     * @param question  用户问题
     */
    public void start(String sessionId, String question) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        cleanupExpired();
        HostTraceRecord record = new HostTraceRecord();
        record.setSessionId(sessionId);
        record.setQuestion(question);
        record.setStatus(HostTraceRecord.STATUS_RUNNING);
        record.setStartTimeMs(System.currentTimeMillis());
        record.setSeq(sequence.incrementAndGet());
        records.put(sessionId, record);
        log.debug("[Trace] start sessionId={}", sessionId);
    }

    /**
     * 记录模型路由结果。
     *
     * @param sessionId 会话 ID
     * @param tier      复杂度档位
     * @param modelName 选中模型
     */
    public void recordModel(String sessionId, String tier, String modelName) {
        withRecord(sessionId, r -> {
            r.setTier(tier);
            r.setModelName(modelName);
        });
        step(sessionId, "route-model", "tier=" + tier + ", model=" + modelName);
    }

    /**
     * 追加一个执行阶段。
     *
     * @param sessionId 会话 ID
     * @param name      阶段名
     * @param detail    阶段详情
     */
    public void step(String sessionId, String name, String detail) {
        withRecord(sessionId, r -> {
            HostTraceRecord.Step s = new HostTraceRecord.Step();
            s.setName(name);
            s.setDetail(detail);
            s.setTimeMs(System.currentTimeMillis());
            r.getSteps().add(s);
        });
    }

    /**
     * 记录最终 SQL。
     *
     * @param sessionId 会话 ID
     * @param sql       待执行 SQL
     */
    public void sql(String sessionId, String sql) {
        withRecord(sessionId, r -> r.setSql(sql));
    }

    /**
     * 记录 HITL 确认结果。
     *
     * @param sessionId 会话 ID
     * @param approved  是否批准
     * @param reason    原因（超时/拒绝等，可为 null）
     */
    public void hitl(String sessionId, Boolean approved, String reason) {
        withRecord(sessionId, r -> r.setHitlApproved(approved));
        step(sessionId, "sql-confirm", "approved=" + approved
                + (reason == null ? "" : ", " + reason));
    }

    /**
     * 记录一次工具调用（事件跟踪器在完成时调用）。
     *
     * @param sessionId  会话 ID
     * @param toolCall   工具调用记录
     */
    public void tool(String sessionId, HostTraceRecord.ToolCallRecord toolCall) {
        withRecord(sessionId, r -> r.getToolCalls().add(toolCall));
    }

    /**
     * 标记完成。
     *
     * @param sessionId 会话 ID
     * @param status    结束状态
     */
    public void finish(String sessionId, String status) {
        withRecord(sessionId, r -> {
            r.setStatus(status);
            r.setEndTimeMs(System.currentTimeMillis());
            r.setDurationMs(Math.max(0, r.getEndTimeMs() - r.getStartTimeMs()));
        });
        log.debug("[Trace] finish sessionId={} status={}", sessionId, status);
    }

    /**
     * 标记失败/取消。
     *
     * @param sessionId 会话 ID
     * @param status    结束状态
     * @param error     原因
     */
    public void fail(String sessionId, String status, String error) {
        withRecord(sessionId, r -> {
            r.setStatus(status);
            r.setError(error);
            r.setEndTimeMs(System.currentTimeMillis());
            r.setDurationMs(Math.max(0, r.getEndTimeMs() - r.getStartTimeMs()));
        });
        log.debug("[Trace] fail sessionId={} status={} error={}",
                sessionId, status, error);
    }

    /**
     * 取单条 trace。
     *
     * @param sessionId 会话 ID
     * @return trace 或 null
     */
    public HostTraceRecord get(String sessionId) {
        cleanupExpired();
        if (sessionId == null) {
            return null;
        }
        return records.get(sessionId);
    }

    /**
     * 取最近 trace 列表（按开始时间倒序，同毫秒按 seq 倒序）。
     *
     * @return trace 列表
     */
    public List<HostTraceRecord> listRecent() {
        cleanupExpired();
        return records.values().stream()
                .sorted(Comparator.comparingLong(
                                HostTraceRecord::getStartTimeMs)
                        .thenComparingLong(HostTraceRecord::getSeq)
                        .reversed())
                .toList();
    }

    /**
     * 对指定会话执行操作（记录不存在时忽略）。
     */
    private void withRecord(
            String sessionId,
            java.util.function.Consumer<HostTraceRecord> consumer) {
        if (sessionId == null) {
            return;
        }
        HostTraceRecord record = records.get(sessionId);
        if (record != null) {
            consumer.accept(record);
        }
    }

    /**
     * 清理 TTL 过期记录并裁剪到上限。
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        if (ttlMs > 0) {
            Iterator<Map.Entry<String, HostTraceRecord>> it =
                    records.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, HostTraceRecord> e = it.next();
                if (now - e.getValue().getStartTimeMs() > ttlMs) {
                    it.remove();
                }
            }
        }
        int over = records.size() - maxRecords;
        if (over > 0) {
            Comparator<Map.Entry<String, HostTraceRecord>> byOldest =
                    Comparator.comparingLong(
                            (Map.Entry<String, HostTraceRecord> e) ->
                                    e.getValue().getStartTimeMs())
                            .thenComparingLong(
                                    (Map.Entry<String, HostTraceRecord> e) ->
                                            e.getValue().getSeq());
            records.entrySet().stream()
                    .sorted(byOldest)
                    .limit(over)
                    .forEach(e -> records.remove(e.getKey()));
        }
    }
}
