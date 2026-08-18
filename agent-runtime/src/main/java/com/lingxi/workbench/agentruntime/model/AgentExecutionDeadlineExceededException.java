package com.lingxi.workbench.agentruntime.model;

/** Agent Runtime 达到本次调用硬截止。 */
public class AgentExecutionDeadlineExceededException extends RuntimeException {

    public AgentExecutionDeadlineExceededException() {
        super("Agent运行已达到时间上限");
    }
}
