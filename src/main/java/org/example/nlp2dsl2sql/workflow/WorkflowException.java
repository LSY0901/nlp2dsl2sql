package org.example.nlp2dsl2sql.workflow;

/**
 * Workflow 业务失败（计划非法、步骤失败、门禁未通过等）。
 */
public class WorkflowException extends RuntimeException {

    /**
     * 构造 Workflow 异常。
     *
     * @param message 友好错误信息
     */
    public WorkflowException(String message) {
        super(message);
    }

    /**
     * 构造带原因的 Workflow 异常。
     *
     * @param message 友好错误信息
     * @param cause   原始异常
     */
    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
