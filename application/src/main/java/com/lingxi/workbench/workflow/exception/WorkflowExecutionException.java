package com.lingxi.workbench.workflow.exception;

/**
 * 工作流运行时异常，表示工作流执行过程中发生不可恢复的错误。
 *
 * <p>涵盖 Agent 调用失败、变量解析失败、定义校验失败等运行时场景。</p>
 */
public class WorkflowExecutionException extends RuntimeException {

    /**
     * 仅包含错误描述的运行时异常。
     *
     * @param message 错误描述
     */
    public WorkflowExecutionException(String message) {
        super(message);
    }

    /**
     * 包含根因的运行时异常。
     *
     * @param message 错误描述
     * @param cause   底层异常
     */
    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
