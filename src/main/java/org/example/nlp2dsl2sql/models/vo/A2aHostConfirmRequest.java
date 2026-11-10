package org.example.nlp2dsl2sql.models.vo;

import lombok.Data;

/**
 * A2A Host SQL 确认请求。
 */
@Data
public class A2aHostConfirmRequest {

    /** 会话 ID */
    private String sessionId;

    /** 确认 ID（来自待确认卡片 confirmId，精确匹配挂起；缺省回退按会话匹配最新一笔） */
    private String confirmId;

    /** 前端预判（仅日志；服务端以 rawInput 为准） */
    private Boolean approved;

    /** 用户原始输入 */
    private String rawInput;
}
