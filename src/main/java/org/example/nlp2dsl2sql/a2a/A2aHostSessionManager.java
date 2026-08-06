package org.example.nlp2dsl2sql.a2a;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A Host Agent 会话缓存管理器。
 * <p>
 * 按 sessionId 缓存与复用 {@link HarnessAgent} 实例，保持多轮对话上下文记忆。
 * 超过指定不活动时间（默认 30 分钟）的会话将被自动清理。
 */
@Slf4j
@Component
public class A2aHostSessionManager {

    /** 默认会话过期时间：30 分钟 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final A2aHostAgentFactory hostAgentFactory;
    private final Map<String, SessionHolder> sessionCache = new ConcurrentHashMap<>();

    public A2aHostSessionManager(A2aHostAgentFactory hostAgentFactory) {
        this.hostAgentFactory = hostAgentFactory;
    }

    /**
     * 获取或创建指定 sessionId 的 HarnessAgent 实例。
     *
     * @param sessionId 会话 ID
     * @param model     路由选出的模型
     * @return HarnessAgent 实例
     */
    public HarnessAgent getOrCreateAgent(String sessionId, OpenAIChatModel model) {
        if (sessionId == null || sessionId.isBlank()) {
            return hostAgentFactory.create(model);
        }

        cleanExpiredSessions();

        SessionHolder holder = sessionCache.compute(sessionId, (key, existing) -> {
            if (existing == null) {
                log.info("[SessionManager] 创建新 Host Agent 会话 sessionId={}", sessionId);
                HarnessAgent newAgent = hostAgentFactory.create(model);
                return new SessionHolder(newAgent);
            }
            existing.touch();
            log.info("[SessionManager] 复用已有 Host Agent 会话 sessionId={}", sessionId);
            return existing;
        });

        return holder.getAgent();
    }

    /**
     * 手动移除指定 sessionId 的会话缓存。
     *
     * @param sessionId 会话 ID
     */
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessionCache.remove(sessionId);
            log.info("[SessionManager] 手动清理会话 sessionId={}", sessionId);
        }
    }

    /**
     * 当前缓存的活动会话数。
     */
    public int getActiveSessionCount() {
        return sessionCache.size();
    }

    /**
     * 清理过期超过 TTL 的闲置会话。
     */
    private void cleanExpiredSessions() {
        Instant now = Instant.now();
        sessionCache.entrySet().removeIf(entry -> {
            boolean expired = Duration.between(entry.getValue().getLastAccessTime(), now).compareTo(DEFAULT_TTL) > 0;
            if (expired) {
                log.info("[SessionManager] 清理超时闲置会话 sessionId={}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * 会话持有对象。
     */
    private static class SessionHolder {
        private final HarnessAgent agent;
        private volatile Instant lastAccessTime;

        public SessionHolder(HarnessAgent agent) {
            this.agent = agent;
            this.lastAccessTime = Instant.now();
        }

        public HarnessAgent getAgent() {
            return agent;
        }

        public Instant getLastAccessTime() {
            return lastAccessTime;
        }

        public void touch() {
            this.lastAccessTime = Instant.now();
        }
    }
}
