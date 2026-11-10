package org.example.nlp2dsl2sql.mapper.memory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionSummary;

import java.util.List;

/**
 * 会话摘要 Mapper。
 */
public interface MemorySessionSummaryMapper extends BaseMapper<MemorySessionSummary> {

    /**
     * 覆盖式写入会话摘要（session_id 冲突则更新）。
     */
    @Insert("""
            INSERT INTO memory_session_summary
                (session_id, user_id, summary, turn_count, first_turn_at, last_turn_at, summarized_at)
            VALUES (#{sessionId}, #{userId}, #{summary}, #{turnCount},
                    #{firstTurnAt}, #{lastTurnAt}, now())
            ON CONFLICT (session_id) DO UPDATE SET
                summary = EXCLUDED.summary, turn_count = EXCLUDED.turn_count,
                first_turn_at = EXCLUDED.first_turn_at, last_turn_at = EXCLUDED.last_turn_at,
                summarized_at = now()
            """)
    int upsert(MemorySessionSummary summary);

    /**
     * 指定用户已有摘要的最大 id（画像增量游标起点）。
     */
    @Select("SELECT COALESCE(MAX(id), 0) FROM memory_session_summary WHERE user_id = #{userId}")
    long maxSummaryId(@Param("userId") String userId);

    /**
     * 摘要表中出现过的全部用户。
     */
    @Select("SELECT DISTINCT user_id FROM memory_session_summary WHERE user_id IS NOT NULL")
    List<String> selectDistinctUserIds();
}
