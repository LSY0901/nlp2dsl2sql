package org.example.nlp2dsl2sql.mapper.memory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionArchive;

import java.util.List;
import java.util.Map;

/**
 * 会话流水 Mapper。
 */
public interface MemorySessionArchiveMapper extends BaseMapper<MemorySessionArchive> {

    /**
     * 找"已结束且有新增轮次待摘要"的会话候选：
     * 末轮早于闲置阈值，且摘要表无记录或末轮时间晚于上次摘要记录的 last_turn_at（支持复用老 sessionId 增量重压缩）。
     *
     * @param idleMinutes 闲置分钟数
     * @return 候选列表（sessionId / userId / lastTurnAt / turnCount）
     */
    @Select("""
            SELECT a.session_id AS "sessionId", MAX(a.user_id) AS "userId",
                   MAX(a.created_at) AS "lastTurnAt", COUNT(*) AS "turnCount"
            FROM memory_session_archive a
            LEFT JOIN memory_session_summary s ON a.session_id = s.session_id
            GROUP BY a.session_id, s.last_turn_at
            HAVING MAX(a.created_at) < now() - (#{idleMinutes} * interval '1 minute')
               AND (s.last_turn_at IS NULL OR MAX(a.created_at) > s.last_turn_at)
            """)
    List<Map<String, Object>> findFinishedSessions(@Param("idleMinutes") int idleMinutes);
}
