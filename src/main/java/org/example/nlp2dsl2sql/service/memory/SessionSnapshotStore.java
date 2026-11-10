package org.example.nlp2dsl2sql.service.memory;

import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;

import java.util.List;

/**
 * 会话语义快照存储接口：短期记忆的读写边界。
 * <p>
 * 当前实现为 Redis（{@code impl.RedisSessionSnapshotStore}）：
 * 每会话一份最新快照 + 最近轮次流水，重启不丢。
 * 读取失败一律返回 null（等价失忆，查询主流程继续），写入失败仅告警。
 */
public interface SessionSnapshotStore {

    /**
     * 获取指定会话的最近一次语义快照。
     *
     * @param sessionId 会话 ID
     * @return 语义快照；无记录或存储异常时返回 null
     */
    SemanticSessionSnapshot get(String sessionId);

    /**
     * 保存/更新指定会话的语义快照，并追加到轮次流水。
     *
     * @param sessionId 会话 ID
     * @param snapshot  语义快照
     */
    void put(String sessionId, SemanticSessionSnapshot snapshot);

    /**
     * 获取指定会话的最近轮次流水（最新在前）。
     *
     * @param sessionId 会话 ID
     * @param limit     最多返回条数
     * @return 轮次列表；无记录或存储异常时返回空列表
     */
    List<SemanticSessionSnapshot> recentTurns(String sessionId, int limit);
}
