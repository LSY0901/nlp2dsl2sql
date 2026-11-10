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
     * 找"已结束且未摘要"的会话候选：末轮早于闲置阈值且摘要表中无记录。
     *
     * @param idleMinutes 闲置分钟数
     * @return 候选列表（sessionId / userId / lastTurnAt / turnCount）
     */
    @Select("""
            SELECT session_id AS "sessionId", user_id AS "userId",
                   MAX(created_at) AS "lastTurnAt", COUNT(*) AS "turnCount"
            FROM memory_session_archive
            GROUP BY session_id, user_id
            HAVING MAX(created_at) < now() - (#{idleMinutes} * interval '1 minute')
              AND session_id NOT IN (SELECT session_id FROM memory_session_summary)
            """)
    List<Map<String, Object>> findFinishedSessions(@Param("idleMinutes") int idleMinutes);
}
