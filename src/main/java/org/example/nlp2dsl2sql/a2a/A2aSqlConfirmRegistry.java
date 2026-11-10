package org.example.nlp2dsl2sql.a2a;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.A2aHostConfirmProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL HITL 挂起表：按 confirmId 登记 / 完成 / 超时清理，支持会话级作废。
 * <p>
 * 键为 confirmId（工具调用 ID），同会话可并存多笔挂起；
 * confirmId 缺失时退化为会话单槽（旧客户端兼容）。
 * SSE 断开或异常时调用 {@link #invalidateSession} 按拒绝结束该会话全部挂起，
 * 避免断开后的确认在无人接收结果的流上盲执行。
 */
@Slf4j
@Component
public class A2aSqlConfirmRegistry {

    private final A2aHostConfirmProperties properties;
    private final Map<String, PendingSqlConfirm> pending = new ConcurrentHashMap<>();

    /**
     * @param properties 确认超时配置
     */
    public A2aSqlConfirmRegistry(A2aHostConfirmProperties properties) {
        this.properties = properties;
    }

    /**
     * 用户确认等待超时毫秒。
     */
    public long getTimeoutMs() {
        return properties.getTimeoutMs();
    }

    /**
     * 登记一次挂起（同键覆盖旧挂起并按拒绝完成）。
     *
     * @param confirm 挂起快照
     */
    public void put(PendingSqlConfirm confirm) {
        if (confirm == null || confirm.getSessionId() == null) {
            return;
        }
        cleanupExpired();
        String key = keyOf(confirm);
        PendingSqlConfirm old = pending.put(key, confirm);
        if (old != null && !old.getDecision().isDone()) {
            old.getDecision().complete(false);
            log.warn("[HITL] 覆盖挂起 confirmId={}", key);
        }
    }

    /**
     * 按 confirmId 取挂起。
     *
     * @param confirmId 确认 ID
     * @return 挂起或 null
     */
    public PendingSqlConfirm get(String confirmId) {
        cleanupExpired();
        if (confirmId == null) {
            return null;
        }
        return pending.get(confirmId);
    }

    /**
     * 按 confirmId 移除挂起。
     *
     * @param confirmId 确认 ID
     * @return 被移除的挂起，可能为 null
     */
    public PendingSqlConfirm remove(String confirmId) {
        if (confirmId == null) {
            return null;
        }
        return pending.remove(confirmId);
    }

    /**
     * 按 confirmId 完成用户决策。
     *
     * @param confirmId 确认 ID
     * @param approved  是否批准
     * @return 是否找到未完成挂起
     */
    public boolean completeByConfirmId(String confirmId, boolean approved) {
        cleanupExpired();
        if (confirmId == null || confirmId.isBlank()) {
            return false;
        }
        PendingSqlConfirm confirm = pending.get(confirmId.trim());
        return complete(confirm, approved);
    }

    /**
     * 按会话完成最新一笔挂起（无 confirmId 的旧客户端兼容路径）。
     *
     * @param sessionId 会话 ID
     * @param approved  是否批准
     * @return 是否找到未完成挂起
     */
    public boolean completeBySession(String sessionId, boolean approved) {
        cleanupExpired();
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        PendingSqlConfirm newest = pending.values().stream()
                .filter(p -> sessionId.trim().equals(p.getSessionId()))
                .max(Comparator.comparingLong(PendingSqlConfirm::getCreatedAtMs))
                .orElse(null);
        return complete(newest, approved);
    }

    /**
     * 会话级作废：按拒绝完成并移除该会话全部挂起，返回作废笔数。
     *
     * @param sessionId 会话 ID
     * @param reason    作废原因（日志与调用方记录用）
     * @return 作废的挂起笔数
     */
    public int invalidateSession(String sessionId, String reason) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, PendingSqlConfirm> e : pending.entrySet()) {
            if (sessionId.equals(e.getValue().getSessionId())) {
                keys.add(e.getKey());
            }
        }
        int count = 0;
        for (String key : keys) {
            PendingSqlConfirm removed = pending.remove(key);
            if (removed == null) {
                continue;
            }
            if (!removed.getDecision().isDone()) {
                removed.getDecision().complete(false);
                log.warn("[HITL] 作废挂起 confirmId={}, reason={}", key, reason);
            }
            count++;
        }
        return count;
    }

    /**
     * 完成挂起决策；已完成的挂起不重复完成。
     */
    private boolean complete(PendingSqlConfirm confirm, boolean approved) {
        if (confirm == null || confirm.getDecision().isDone()) {
            return false;
        }
        return confirm.getDecision().complete(approved);
    }

    /**
     * 挂起键：confirmId 优先，缺失时退化为会话单槽键。
     */
    private String keyOf(PendingSqlConfirm confirm) {
        if (confirm.getConfirmId() != null && !confirm.getConfirmId().isBlank()) {
            return confirm.getConfirmId();
        }
        return "session:" + confirm.getSessionId();
    }

    /**
     * 清理超时挂起（按拒绝完成）。
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        long timeoutMs = getTimeoutMs();
        pending.entrySet().removeIf(e -> {
            if (now - e.getValue().getCreatedAtMs() <= timeoutMs) {
                return false;
            }
            if (!e.getValue().getDecision().isDone()) {
                e.getValue().getDecision().complete(false);
                log.warn("[HITL] 确认超时 confirmId={}", e.getKey());
            }
            return true;
        });
    }
}
