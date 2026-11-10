package org.example.nlp2dsl2sql.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionSummaryMapper;
import org.example.nlp2dsl2sql.mapper.memory.MemoryUserProfileMapper;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionSummary;
import org.example.nlp2dsl2sql.models.entity.memory.MemoryUserProfile;
import org.example.nlp2dsl2sql.service.memory.SessionSnapshotStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话记忆只读查看端点：联调时直接看 Redis/PG 里的记忆内容。
 */
@RestController
@RequestMapping("/aiChat")
public class MemoryController {

    private final SessionSnapshotStore snapshotStore;
    private final MemorySessionSummaryMapper summaryMapper;
    private final MemoryUserProfileMapper profileMapper;

    /**
     * @param snapshotStore 短期记忆存储
     * @param summaryMapper 摘要 Mapper
     * @param profileMapper 画像 Mapper
     */
    public MemoryController(SessionSnapshotStore snapshotStore,
                            MemorySessionSummaryMapper summaryMapper,
                            MemoryUserProfileMapper profileMapper) {
        this.snapshotStore = snapshotStore;
        this.summaryMapper = summaryMapper;
        this.profileMapper = profileMapper;
    }

    /**
     * 查看指定会话的记忆全景：Redis 快照 + 最近轮次 + PG 摘要。
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("snapshot", snapshotStore.get(sessionId));
        result.put("recentTurns", snapshotStore.recentTurns(sessionId, 50));
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
