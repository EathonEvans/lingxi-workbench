package com.lingxi.workbench.workflow.exception;

/**
 * 工作流定义不满足编译约束。
 */
public class WorkflowDefinitionException extends RuntimeException {

    /**
     * @param message 校验失败的错误描述
     */
    public WorkflowDefinitionException(String message) {
        super(message);
    }

    /**
     * @param message 校验失败的错误描述
     * @param cause   原始异常
     */
    public WorkflowDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
