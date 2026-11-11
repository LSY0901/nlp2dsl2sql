package org.example.nlp2dsl2sql.service.memory;

import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;

/**
 * 会话语义快照存储接口：短期记忆的读写边界。
 * <p>
 * 当前实现为 Redis（{@code impl.RedisSessionSnapshotStore}）：
 * 每会话一份最新语义快照（仅当 SQL 查询成功产出 DSL 时更新，防 SOP/闲聊冲掉槽位），重启不丢。
 * 完整问答流水只写 PG {@code memory_session_archive}，不在 Redis 再存一份。
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
     * 保存/覆盖指定会话的最新语义 DSL 快照。
     *
     * @param sessionId 会话 ID
     * @param snapshot  含非空 DSL 的语义快照
     */
    void putSnapshot(String sessionId, SemanticSessionSnapshot snapshot);
}
