package org.example.nlp2dsl2sql.mapper.memory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.example.nlp2dsl2sql.models.entity.memory.MemoryUserProfile;

/**
 * 用户画像 Mapper。
 */
public interface MemoryUserProfileMapper extends BaseMapper<MemoryUserProfile> {

    /**
     * 覆盖式写入用户画像（user_id 冲突则更新）。
     */
    @Insert("""
            INSERT INTO memory_user_profile (user_id, profile_json, summary_seen, updated_at)
            VALUES (#{userId}, #{profileJson}, #{summarySeen}, now())
            ON CONFLICT (user_id) DO UPDATE SET
                profile_json = EXCLUDED.profile_json,
                summary_seen = EXCLUDED.summary_seen, updated_at = now()
            """)
    int upsert(MemoryUserProfile profile);
}
