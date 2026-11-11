package org.example.nlp2dsl2sql.models.entity.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 会话流水：每轮对话一行，写穿落库（长期记忆压缩原料）。
 */
@Data
@TableName("memory_session_archive")
public class MemorySessionArchive {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 归属用户 ID（可空，派生规则见 UserKeyResolver） */
    private String userId;

    /** 用户原始问句 */
    private String question;

    /** 上下文补全后的自包含问句 */
    private String effectiveQuestion;

    /** 语义 DSL 的 fastjson2 序列化（非 SQL 轮次可空） */
    private String dslJson;

    /** 截断后的回答结论 */
    private String answer;

    /** 轮次类型：SQL / SOP / MIXED / CHITCHAT */
    private String turnType;

    /** 入库时间（DB 默认 now()） */
    private OffsetDateTime createdAt;
}
