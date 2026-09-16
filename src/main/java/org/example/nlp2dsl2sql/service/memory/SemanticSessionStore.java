package org.example.nlp2dsl2sql.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内轻量级会话语义存储：维护每个 sessionId 的多轮问数语义快照。
 * <p>
 * 默认 30 分钟闲置过期自动清理。
 */
@Slf4j
@Component
public class SemanticSessionStore {

    /** 默认会话语义快照过期时间：30 分钟 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final Map<String, StoreEntry> cache = new ConcurrentHashMap<>();

    /**
     * 获取指定会话的最近一次语义快照。
     *
     * @param sessionId 会话 ID
     * @return 语义快照，若无或已过期返回 null
     */
    public SemanticSessionSnapshot get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        cleanExpired();
        StoreEntry entry = cache.get(sessionId.trim());
        if (entry == null) {
            return null;
        }
        entry.touch();
        return entry.snapshot;
    }

    /**
     * 保存/更新指定会话的语义快照。
     *
     * @param sessionId 会话 ID
     * @param snapshot  语义快照
     */
    public void put(String sessionId, SemanticSessionSnapshot snapshot) {
        if (sessionId == null || sessionId.isBlank() || snapshot == null) {
            return;
        }
        cleanExpired();
        cache.put(sessionId.trim(), new StoreEntry(snapshot));
        log.info("[SemanticStore] 更新会话语义快照 sessionId={}, question={}",
                sessionId.trim(), snapshot.getQuestion());
    }

    /**
     * 清理指定会话。
     *
     * @param sessionId 会话 ID
     */
    public void remove(String sessionId) {
        if (sessionId != null) {
            cache.remove(sessionId.trim());
            log.info("[SemanticStore] 清理会话语义快照 sessionId={}", sessionId.trim());
        }
    }

    /**
     * 当前缓存的会话数。
     */
    public int size() {
        return cache.size();
    }

    /**
     * 清理过期闲置条目。
     */
    private void cleanExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(e -> {
            boolean expired = Duration.between(e.getValue().lastAccessTime, now).compareTo(DEFAULT_TTL) > 0;
            if (expired) {
                log.debug("[SemanticStore] 清理超时闲置语义快照 sessionId={}", e.getKey());
            }
            return expired;
        });
    }

    private static class StoreEntry {
        private final SemanticSessionSnapshot snapshot;
        private volatile Instant lastAccessTime;

        public StoreEntry(SemanticSessionSnapshot snapshot) {
            this.snapshot = snapshot;
            this.lastAccessTime = Instant.now();
        }

        public void touch() {
            this.lastAccessTime = Instant.now();
        }
    }
}
