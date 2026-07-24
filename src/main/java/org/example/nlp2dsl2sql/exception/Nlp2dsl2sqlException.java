package org.example.nlp2dsl2sql.exception;

/**
 * Workflow 业务失败（计划非法、步骤失败、门禁未通过等）。
 */
public class Nlp2dsl2sqlException extends RuntimeException {

    /**
     * 构造 Workflow 异常。
     *
     * @param message 友好错误信息
     */
    public Nlp2dsl2sqlException(String message) {
        super(message);
    }

    /**
     * 构造带原因的 Workflow 异常。
     *
     * @param message 友好错误信息
     * @param cause   原始异常
     */
    public Nlp2dsl2sqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
