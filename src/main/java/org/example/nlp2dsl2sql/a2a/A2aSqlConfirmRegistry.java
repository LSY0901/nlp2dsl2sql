package org.example.nlp2dsl2sql.a2a;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * SQL HITL 挂起表：按 sessionId 登记 / 完成 / 超时清理。
 */
@Slf4j
@Component
public class A2aSqlConfirmRegistry {

    /** 确认超时：5 分钟 */
    public static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    private final ConcurrentHashMap<String, PendingSqlConfirm> pending =
            new ConcurrentHashMap<>();

    /**
     * 登记一次挂起（同 session 覆盖旧挂起并按拒绝完成）。
     *
     * @param confirm 挂起快照
     */
    public void put(PendingSqlConfirm confirm) {
        if (confirm == null || confirm.getSessionId() == null) {
            return;
        }
        cleanupExpired();
        PendingSqlConfirm old = pending.put(confirm.getSessionId(), confirm);
        if (old != null && !old.getDecision().isDone()) {
            old.getDecision().complete(false);
            log.warn("[HITL] 覆盖挂起 sessionId={}", confirm.getSessionId());
        }
    }

    /**
     * 按 sessionId 取挂起。
     *
     * @param sessionId 会话 ID
     * @return 挂起或 null
     */
    public PendingSqlConfirm get(String sessionId) {
        cleanupExpired();
        if (sessionId == null) {
            return null;
        }
        return pending.get(sessionId);
    }

    /**
     * 移除挂起。
     *
     * @param sessionId 会话 ID
     * @return 被移除的挂起，可能为 null
     */
    public PendingSqlConfirm remove(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return pending.remove(sessionId);
    }

    /**
     * 完成用户决策。
     *
     * @param sessionId 会话 ID
     * @param approved  是否批准
     * @return 是否找到未完成挂起
     */
    public boolean complete(String sessionId, boolean approved) {
        cleanupExpired();
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        PendingSqlConfirm confirm = pending.get(sessionId.trim());
        if (confirm == null || confirm.getDecision().isDone()) {
            return false;
        }
        return confirm.getDecision().complete(approved);
    }

    /**
     * 清理超时挂起（按拒绝完成）。
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingSqlConfirm>> it =
                pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingSqlConfirm> e = it.next();
            PendingSqlConfirm c = e.getValue();
            if (now - c.getCreatedAtMs() <= TIMEOUT_MS) {
                continue;
            }
            it.remove();
            if (!c.getDecision().isDone()) {
                c.getDecision().complete(false);
                log.warn("[HITL] 确认超时 sessionId={}", e.getKey());
            }
        }
    }
}
