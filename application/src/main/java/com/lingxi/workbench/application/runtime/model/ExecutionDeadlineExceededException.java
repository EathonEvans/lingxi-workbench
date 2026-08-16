package com.lingxi.workbench.application.runtime.model;

/** Application 单轮执行达到硬截止或一次性流式宽限后的稳定异常。 */
public class ExecutionDeadlineExceededException extends RuntimeException {

    public ExecutionDeadlineExceededException() {
        super("Application单轮执行已达到时间上限");
    }
}
