package org.example.nlp2dsl2sql.models.entity.memory;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话摘要：闲置会话经 LLM 压缩后的产物，每会话一条覆盖式更新。
 */
@Data
@TableName("memory_session_summary")
public class MemorySessionSummary {

    private Long id;

    /** 会话 ID（UNIQUE） */
    private String sessionId;

    /** 归属用户 ID（可空） */
    private String userId;

    /** LLM 压缩摘要 */
    private String summary;

    /** 会话轮数 */
    private Integer turnCount;

    /** 首轮时间 */
    private LocalDateTime firstTurnAt;

    /** 末轮时间 */
    private LocalDateTime lastTurnAt;

    /** 摘要生成时间 */
    private LocalDateTime summarizedAt;
}
