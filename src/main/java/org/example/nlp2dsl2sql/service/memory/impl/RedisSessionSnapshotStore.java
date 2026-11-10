package org.example.nlp2dsl2sql.service.memory.impl;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;
import org.example.nlp2dsl2sql.service.memory.SessionSnapshotStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Redis 短期会话记忆：最新快照（String）+ 轮次流水（List）。
 * <p>
 * 键与序列化约定：值一律 fastjson2 JSON 字符串；
 * 快照 {@code nlp2dsl2sql:memory:snapshot:{sessionId}}，TTL = {@code memory.session-ttl}；
 * 轮次 {@code nlp2dsl2sql:memory:turns:{sessionId}}，LPUSH 最新在前，仅保留最近 N 条。
 * <p>
 * 读取失败返回 null / 空列表（等价失忆，不阻断查询主流程）；写入失败仅 log.warn。
 */
@Slf4j
@Component
public class RedisSessionSnapshotStore implements SessionSnapshotStore {

    /** 快照键前缀 */
    public static final String KEY_SNAPSHOT_PREFIX = "nlp2dsl2sql:memory:snapshot:";

    /** 轮次流水键前缀 */
    public static final String KEY_TURNS_PREFIX = "nlp2dsl2sql:memory:turns:";

    private final StringRedisTemplate redisTemplate;
    private final MemoryProperties memoryProperties;

    /**
     * @param redisTemplate   Redis 模板（String/String）
     * @param memoryProperties 会话记忆配置（session-ttl）
     */
    public RedisSessionSnapshotStore(
            StringRedisTemplate redisTemplate,
            MemoryProperties memoryProperties) {
        this.redisTemplate = redisTemplate;
        this.memoryProperties = memoryProperties;
    }

    @Override
    public SemanticSessionSnapshot get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue()
                    .get(KEY_SNAPSHOT_PREFIX + sessionId.trim());
            if (json == null || json.isBlank()) {
                return null;
            }
            return JSON.parseObject(json, SemanticSessionSnapshot.class);
        } catch (Exception e) {
            log.warn("[MemoryStore] 读取快照失败，降级为无记忆: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String sessionId, SemanticSessionSnapshot snapshot) {
        if (sessionId == null || sessionId.isBlank() || snapshot == null) {
            return;
        }
        String sid = sessionId.trim();
        Duration ttl = resolveTtl();
        String json = JSON.toJSONString(snapshot);
        try {
            redisTemplate.opsForValue()
                    .set(KEY_SNAPSHOT_PREFIX + sid, json, ttl);

            String turnsKey = KEY_TURNS_PREFIX + sid;
            redisTemplate.opsForList().leftPush(turnsKey, json);
            redisTemplate.opsForList().trim(turnsKey, 0, (long) turnsKeep() - 1);
            redisTemplate.expire(turnsKey, ttl);

            log.info("[MemoryStore] 快照写入 Redis sessionId={}, question={}",
                    sid, snapshot.getQuestion());
        } catch (Exception e) {
            log.warn("[MemoryStore] 快照写入 Redis 失败: sessionId={}, error={}",
                    sid, e.getMessage());
        }
    }

    @Override
    public List<SemanticSessionSnapshot> recentTurns(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            List<String> jsons = redisTemplate.opsForList()
                    .range(KEY_TURNS_PREFIX + sessionId.trim(), 0, limit - 1);
            if (jsons == null) {
                return List.of();
            }
            return jsons.stream()
                    .filter(Objects::nonNull)
                    .map(json -> JSON.parseObject(json, SemanticSessionSnapshot.class))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("[MemoryStore] 读取轮次失败: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 会话 TTL；配置缺失或非法时兜底 24h。
     */
    private Duration resolveTtl() {
        Duration ttl = memoryProperties.getSessionTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Duration.ofHours(24);
        }
        return ttl;
    }

    /**
     * 轮次流水保留条数；非法配置兜底 50。
     */
    private int turnsKeep() {
        int keep = memoryProperties.getLongterm().getTurnsKeep();
        return keep > 0 ? keep : 50;
    }
}
