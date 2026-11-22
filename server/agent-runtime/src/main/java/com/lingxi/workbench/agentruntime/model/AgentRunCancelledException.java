package com.lingxi.workbench.agentruntime.model;

/** 用户显式取消 Agent Runtime 调用。 */
public class AgentRunCancelledException extends RuntimeException {

    public AgentRunCancelledException() {
        super("Agent运行已取消");
    }
}
