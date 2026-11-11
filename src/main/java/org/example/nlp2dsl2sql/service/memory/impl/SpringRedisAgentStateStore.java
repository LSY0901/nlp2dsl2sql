package org.example.nlp2dsl2sql.service.memory.impl;

import com.alibaba.fastjson2.JSON;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.service.memory.MemorySummaryJob;
import org.example.nlp2dsl2sql.service.memory.UserKeyResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 Spring Data Redis 的 AgentScope {@link AgentStateStore} 实现。
 * <p>
 * 职责：
 * <ul>
 *   <li>替代本地 {@code JsonFileAgentStateStore} 与进程内会话缓存，将多轮对话消息状态统一托管至 Redis；</li>
 *   <li>保存 {@link AgentState} 时按 {@code memory.max-state-messages} 执行滑动窗口截断，
 *       对齐 {@link MsgRole#USER} 边界防止留下孤立 {@code TOOL} 消息或半截 {@code tool_call}；</li>
 *   <li>发生窗口截断溢出时，立即异步触发 {@link MemorySummaryJob#summarizeSessionAsync} 将早期轮次压缩入 PG。</li>
 * </ul>
 * 任何 Redis 读写异常均静默降级并记录告警日志，绝不阻断对话主流程。
 */
@Slf4j
@Component
public class SpringRedisAgentStateStore implements AgentStateStore {

    /** 单值状态键前缀：nlp2dsl2sql:memory:state:{sessionId}:{agentName}:{stateKey}（经 {@link #forAgent} 视图写入） */
    public static final String KEY_STATE_PREFIX = "nlp2dsl2sql:memory:state:";

    /** 列表状态键前缀：nlp2dsl2sql:memory:state-list:{sessionId}:{stateKey} */
    public static final String KEY_STATE_LIST_PREFIX = "nlp2dsl2sql:memory:state-list:";

    /** ReActAgent 默认的 AgentState 存储键名 */
    public static final String DEFAULT_AGENT_STATE_KEY = "agent_state";

    private final StringRedisTemplate redisTemplate;
    private final MemoryProperties memoryProperties;
    private final MemorySummaryJob summaryJob;
    private final UserKeyResolver userKeyResolver;

    /**
     * 兼容双参构造（仅供单测便捷构造）。
     */
    public SpringRedisAgentStateStore(
            StringRedisTemplate redisTemplate,
            MemoryProperties memoryProperties) {
        this(redisTemplate, memoryProperties, null, new UserKeyResolver());
    }

    /**
     * @param redisTemplate    Redis 模板
     * @param memoryProperties 记忆配置
     * @param summaryJob       会话摘要任务（窗口截断时触发异步压缩）
     * @param userKeyResolver  userId 派生器
     */
    @Autowired
    public SpringRedisAgentStateStore(
            StringRedisTemplate redisTemplate,
            MemoryProperties memoryProperties,
            MemorySummaryJob summaryJob,
            UserKeyResolver userKeyResolver) {
        this.redisTemplate = redisTemplate;
        this.memoryProperties = memoryProperties != null ? memoryProperties : new MemoryProperties();
        this.summaryJob = summaryJob;
        this.userKeyResolver = userKeyResolver != null ? userKeyResolver : new UserKeyResolver();
    }

    @Override
    public void save(String userId, String sessionId, String stateKey, State state) {
        if (sessionId == null || sessionId.isBlank()
                || stateKey == null || stateKey.isBlank()
                || state == null) {
            return;
        }
        String sid = sessionId.trim();
        String key = stateKey.trim();

        if (state instanceof AgentState agentState) {
            evictOverflowMessagesIfNeeded(userId, sid, agentState);
        }

        try {
            String json = JsonUtils.getJsonCodec().toJson(state);
            redisTemplate.opsForValue().set(
                    buildStateKey(sid, key), json, resolveTtl());
        } catch (Exception e) {
            log.warn("[RedisStateStore] 保存状态失败: sessionId={}, stateKey={}, error={}",
                    sid, key, e.getMessage());
        }
    }

    @Override
    public void save(String userId, String sessionId, String stateKey, List<? extends State> states) {
        if (sessionId == null || sessionId.isBlank()
                || stateKey == null || stateKey.isBlank()
                || states == null) {
            return;
        }
        String sid = sessionId.trim();
        String key = stateKey.trim();
        try {
            List<String> serializedItems = new ArrayList<>(states.size());
            for (State item : states) {
                if (item != null) {
                    serializedItems.add(JsonUtils.getJsonCodec().toJson(item));
                }
            }
            String json = JSON.toJSONString(serializedItems);
            redisTemplate.opsForValue().set(
                    buildStateListKey(sid, key), json, resolveTtl());
        } catch (Exception e) {
            log.warn("[RedisStateStore] 保存列表状态失败: sessionId={}, stateKey={}, error={}",
                    sid, key, e.getMessage());
        }
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String stateKey, Class<T> type) {
        if (sessionId == null || sessionId.isBlank()
                || stateKey == null || stateKey.isBlank()
                || type == null) {
            return Optional.empty();
        }
        String sid = sessionId.trim();
        String key = stateKey.trim();
        try {
            String json = redisTemplate.opsForValue().get(buildStateKey(sid, key));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            T state = JsonUtils.getJsonCodec().fromJson(json, type);
            return Optional.ofNullable(state);
        } catch (Exception e) {
            log.warn("[RedisStateStore] 读取状态失败，降级为空状态: sessionId={}, stateKey={}, error={}",
                    sid, key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String stateKey, Class<T> type) {
        if (sessionId == null || sessionId.isBlank()
                || stateKey == null || stateKey.isBlank()
                || type == null) {
            return List.of();
        }
        String sid = sessionId.trim();
        String key = stateKey.trim();
        try {
            String json = redisTemplate.opsForValue().get(buildStateListKey(sid, key));
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<String> items = JSON.parseArray(json, String.class);
            if (items == null || items.isEmpty()) {
                return List.of();
            }
            List<T> result = new ArrayList<>(items.size());
            for (String itemJson : items) {
                if (itemJson != null && !itemJson.isBlank()) {
                    T decoded = JsonUtils.getJsonCodec().fromJson(itemJson, type);
                    if (decoded != null) {
                        result.add(decoded);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[RedisStateStore] 读取列表状态失败: sessionId={}, stateKey={}, error={}",
                    sid, key, e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return existsKey(sessionId, DEFAULT_AGENT_STATE_KEY);
    }

    @Override
    public void delete(String userId, String sessionId) {
        delete(userId, sessionId, DEFAULT_AGENT_STATE_KEY);
    }

    @Override
    public void delete(String userId, String sessionId, String stateKey) {
        if (sessionId == null || sessionId.isBlank()
                || stateKey == null || stateKey.isBlank()) {
            return;
        }
        String sid = sessionId.trim();
        String key = stateKey.trim();
        try {
            redisTemplate.delete(List.of(
                    buildStateKey(sid, key),
                    buildStateListKey(sid, key)));
        } catch (Exception e) {
            log.warn("[RedisStateStore] 删除状态失败: sessionId={}, stateKey={}, error={}",
                    sid, key, e.getMessage());
        }
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return Set.of();
    }

    /**
     * 返回按 Agent 隔离命名空间的视图：stateKey 变为 {@code {agentName}:{stateKey}}。
     * <p>
     * 五种编排形态共用同一个 sessionId（前端 localStorage），若 Host / ReAct / Skill 共用同一个
     * {@code agent_state} 键，会把别的 Agent 的 tool_call 消息恢复进来（工具不存在、提示词不同），
     * 因此每个 Agent 必须挂自己的命名空间视图。
     *
     * @param agentName Agent 名（与 ReActAgent#name 一致）
     * @return 命名空间隔离的 AgentStateStore
     */
    public AgentStateStore forAgent(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName 不能为空");
        }
        return new NamespacedView(agentName.trim());
    }

    /**
     * 获取指定会话、指定 Agent 当前在 Redis AgentState 中缓存的短期消息条数（供 MemoryController 查看）。
     *
     * @param sessionId 会话 ID
     * @param agentName Agent 名
     * @return 消息条数；不存在或异常时返回 0
     */
    public int getContextMessageCount(String sessionId, String agentName) {
        return get(null, sessionId, agentName + ":" + DEFAULT_AGENT_STATE_KEY, AgentState.class)
                .map(AgentState::getContext)
                .filter(Objects::nonNull)
                .map(List::size)
                .orElse(0);
    }

    /**
     * 命名空间视图：所有 stateKey 加 {@code {namespace}:} 前缀后委托给外层实现（截断/摘要触发逻辑不变）。
     */
    private final class NamespacedView implements AgentStateStore {

        private final String namespace;

        private NamespacedView(String namespace) {
            this.namespace = namespace;
        }

        private String ns(String stateKey) {
            return namespace + ":" + (stateKey == null ? "" : stateKey.trim());
        }

        @Override
        public void save(String userId, String sessionId, String stateKey, State state) {
            SpringRedisAgentStateStore.this.save(userId, sessionId, ns(stateKey), state);
        }

        @Override
        public void save(String userId, String sessionId, String stateKey,
                         List<? extends State> states) {
            SpringRedisAgentStateStore.this.save(userId, sessionId, ns(stateKey), states);
        }

        @Override
        public <T extends State> Optional<T> get(
                String userId, String sessionId, String stateKey, Class<T> type) {
            return SpringRedisAgentStateStore.this.get(userId, sessionId, ns(stateKey), type);
        }

        @Override
        public <T extends State> List<T> getList(
                String userId, String sessionId, String stateKey, Class<T> type) {
            return SpringRedisAgentStateStore.this.getList(userId, sessionId, ns(stateKey), type);
        }

        @Override
        public boolean exists(String userId, String sessionId) {
            return SpringRedisAgentStateStore.this.existsKey(sessionId, ns(DEFAULT_AGENT_STATE_KEY));
        }

        @Override
        public void delete(String userId, String sessionId) {
            SpringRedisAgentStateStore.this.delete(userId, sessionId, ns(DEFAULT_AGENT_STATE_KEY));
        }

        @Override
        public void delete(String userId, String sessionId, String stateKey) {
            SpringRedisAgentStateStore.this.delete(userId, sessionId, ns(stateKey));
        }

        @Override
        public Set<String> listSessionIds(String userId) {
            return Set.of();
        }
    }

    private boolean existsKey(String sessionId, String stateKey) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(
                    buildStateKey(sessionId.trim(), stateKey)));
        } catch (Exception e) {
            log.warn("[RedisStateStore] 检查状态存在失败: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * 按完整对话轮次（对齐 {@link MsgRole#USER} 边界）对 {@link AgentState#contextMutable()} 执行滑动窗口截断，
     * 并在发生截断时触发异步 PG 摘要压缩。
     */
    private void evictOverflowMessagesIfNeeded(String userId, String sessionId, AgentState agentState) {
        int maxMessages = memoryProperties.getMaxStateMessages();
        if (maxMessages <= 0) {
            return;
        }
        List<Msg> context = agentState.contextMutable();
        if (context == null || context.size() <= maxMessages) {
            return;
        }

        int removeFrom = (context.get(0) != null && context.get(0).getRole() == MsgRole.SYSTEM)
                ? 1 : 0;
        int startSearch = Math.max(removeFrom, context.size() - maxMessages);

        int cutIdx = -1;
        for (int i = startSearch; i < context.size(); i++) {
            if (isUserTurnStart(context.get(i))) {
                cutIdx = i;
                break;
            }
        }
        // 若最新一轮工具调用特别长导致 startSearch 之后无 USER 消息，则向前找最近一个可截断的 USER 边界
        if (cutIdx <= removeFrom) {
            for (int i = startSearch - 1; i > removeFrom; i--) {
                if (isUserTurnStart(context.get(i))) {
                    cutIdx = i;
                    break;
                }
            }
        }

        if (cutIdx > removeFrom) {
            int evicted = cutIdx - removeFrom;
            context.subList(removeFrom, cutIdx).clear();
            log.info("[RedisStateStore] 滑动窗口截断 sessionId={}, evicted={}, remaining={}",
                    sessionId, evicted, context.size());
            if (summaryJob != null) {
                try {
                    String resolvedUid = userKeyResolver.resolve(
                            sessionId,
                            (userId != null && !userId.isBlank()) ? userId : agentState.getUserId());
                    summaryJob.summarizeSessionAsync(sessionId, resolvedUid);
                } catch (Exception e) {
                    log.warn("[RedisStateStore] 触发窗口溢出异步摘要失败: sessionId={}, error={}",
                            sessionId, e.getMessage());
                }
            }
        }
    }

    /**
     * 判断消息是否为新一轮用户提问起点（排除 HITL ConfirmResult 恢复消息）。
     */
    private boolean isUserTurnStart(Msg msg) {
        if (msg == null || msg.getRole() != MsgRole.USER) {
            return false;
        }
        if (msg.getMetadata() != null
                && msg.getMetadata().containsKey(Msg.METADATA_CONFIRM_RESULTS)) {
            return false;
        }
        return true;
    }

    private String buildStateKey(String sessionId, String stateKey) {
        return KEY_STATE_PREFIX + sessionId + ":" + stateKey;
    }

    private String buildStateListKey(String sessionId, String stateKey) {
        return KEY_STATE_LIST_PREFIX + sessionId + ":" + stateKey;
    }

    private Duration resolveTtl() {
        Duration ttl = memoryProperties.getSessionTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Duration.ofHours(24);
        }
        return ttl;
    }
}
