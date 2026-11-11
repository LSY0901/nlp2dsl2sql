package org.example.nlp2dsl2sql.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.nlp2dsl2sql.a2a.A2aHostAgentFactory;
import org.example.nlp2dsl2sql.agent.Nlp2dsl2sqlAgent;
import org.example.nlp2dsl2sql.agent.Nlp2dsl2sqlSkillHarnessAgent;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionArchiveMapper;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionSummaryMapper;
import org.example.nlp2dsl2sql.mapper.memory.MemoryUserProfileMapper;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionArchive;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionSummary;
import org.example.nlp2dsl2sql.models.entity.memory.MemoryUserProfile;
import org.example.nlp2dsl2sql.service.memory.SessionSnapshotStore;
import org.example.nlp2dsl2sql.service.memory.impl.SpringRedisAgentStateStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话记忆只读查看端点：联调时直接看 Redis/PG 里的记忆内容。
 */
@RestController
@RequestMapping("/aiChat")
public class MemoryController {

    /** 记忆视图里展示的最近 PG 流水条数 */
    private static final int RECENT_TURNS_LIMIT = 50;

    private final SessionSnapshotStore snapshotStore;
    private final MemorySessionArchiveMapper archiveMapper;
    private final MemorySessionSummaryMapper summaryMapper;
    private final MemoryUserProfileMapper profileMapper;
    private final SpringRedisAgentStateStore stateStore;

    /**
     * @param snapshotStore 短期快照存储
     * @param archiveMapper PG 流水 Mapper
     * @param summaryMapper 摘要 Mapper
     * @param profileMapper 画像 Mapper
     * @param stateStore    Redis AgentState 存储
     */
    public MemoryController(SessionSnapshotStore snapshotStore,
                            MemorySessionArchiveMapper archiveMapper,
                            MemorySessionSummaryMapper summaryMapper,
                            MemoryUserProfileMapper profileMapper,
                            SpringRedisAgentStateStore stateStore) {
        this.snapshotStore = snapshotStore;
        this.archiveMapper = archiveMapper;
        this.summaryMapper = summaryMapper;
        this.profileMapper = profileMapper;
        this.stateStore = stateStore;
    }

    /**
     * 查看指定会话的记忆全景：各 Agent 的 Redis 消息条数 + Redis 快照 + PG 最近流水 + PG 摘要。
     *
     * @param sessionId 会话 ID
     * @return 记忆视图
     */
    @GetMapping(value = "/memory/session/{sessionId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> sessionMemory(@PathVariable String sessionId) {
        MemorySessionSummary summary = summaryMapper.selectOne(
                new LambdaQueryWrapper<MemorySessionSummary>()
                        .eq(MemorySessionSummary::getSessionId, sessionId));
        List<MemorySessionArchive> recentTurns = archiveMapper.selectList(
                new LambdaQueryWrapper<MemorySessionArchive>()
                        .eq(MemorySessionArchive::getSessionId, sessionId)
                        .orderByDesc(MemorySessionArchive::getCreatedAt)
                        .last("LIMIT " + RECENT_TURNS_LIMIT));

        Map<String, Object> stateCounts = new LinkedHashMap<>();
        for (String agent : List.of(A2aHostAgentFactory.AGENT_NAME,
                Nlp2dsl2sqlAgent.AGENT_NAME, Nlp2dsl2sqlSkillHarnessAgent.AGENT_NAME)) {
            stateCounts.put(agent, stateStore.getContextMessageCount(sessionId, agent));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("stateMessageCount", stateCounts);
        result.put("snapshot", snapshotStore.get(sessionId));
        result.put("recentTurns", recentTurns);
        result.put("summary", summary);
        return result;
    }

    /**
     * 查看指定用户的长期画像。
     *
     * @param userId 用户 ID
     * @return 画像视图；无画像时 profile 为 null
     */
    @GetMapping(value = "/memory/user/{userId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> userMemory(@PathVariable String userId) {
        MemoryUserProfile profile = profileMapper.selectById(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("profile", profile);
        return result;
    }
}
