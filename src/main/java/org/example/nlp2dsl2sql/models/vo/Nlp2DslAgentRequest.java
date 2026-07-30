package org.example.nlp2dsl2sql.models.vo;

import lombok.Data;

/**
 * NLP2DSL 查询请求。
 */
@Data
public class Nlp2DslAgentRequest {

    /** 用户自然语言问题 */
    private String question;

    /** A2A Host HITL 会话 ID（前端生成；缺失时服务端生成） */
    private String sessionId;
}
