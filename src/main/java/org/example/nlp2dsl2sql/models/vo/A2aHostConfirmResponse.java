package org.example.nlp2dsl2sql.models.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A Host SQL 确认响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class A2aHostConfirmResponse {

    /** 是否受理成功 */
    private boolean ok;

    /** 说明文案 */
    private String message;

    /**
     * 成功响应。
     *
     * @param message 说明
     * @return 响应
     */
    public static A2aHostConfirmResponse ok(String message) {
        return new A2aHostConfirmResponse(true, message);
    }

    /**
     * 失败响应。
     *
     * @param message 说明
     * @return 响应
     */
    public static A2aHostConfirmResponse fail(String message) {
        return new A2aHostConfirmResponse(false, message);
    }
}
