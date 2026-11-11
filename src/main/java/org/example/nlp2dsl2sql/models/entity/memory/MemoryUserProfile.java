package org.example.nlp2dsl2sql.models.entity.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户画像：从会话摘要提炼的四字段 JSON，每用户一条覆盖式更新。
 */
@Data
@TableName("memory_user_profile")
public class MemoryUserProfile {

    /** 用户 ID（主键） */
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;

    /** 画像 JSON：focusAreas / preferredMetrics / preferredDimensions / notes */
    private String profileJson;

    /** 已消化到哪条摘要 id（增量提炼游标） */
    private Long summarySeen;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
