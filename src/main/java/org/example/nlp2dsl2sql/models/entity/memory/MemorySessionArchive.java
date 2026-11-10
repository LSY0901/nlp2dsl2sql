package org.example.nlp2dsl2sql.models.entity.memory;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话流水：每轮问数一行，写穿落库（长期记忆原料）。
 */
@Data
@TableName("memory_session_archive")
public class MemorySessionArchive {

    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 归属用户 ID（可空，派生规则见 UserKeyResolver） */
    private String userId;

    /** 用户原始问句 */
    private String question;

    /** 上下文补全后的自包含问句 */
    private String effectiveQuestion;

    /** 语义 DSL 的 fastjson2 序列化 */
    private String dslJson;

    /** 入库时间（DB 默认 now()） */
    private LocalDateTime createdAt;
}
