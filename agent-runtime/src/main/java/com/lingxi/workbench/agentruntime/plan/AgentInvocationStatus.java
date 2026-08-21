package com.lingxi.workbench.agentruntime.plan;

/** 单个计划步骤的隔离调用状态。 */
public enum AgentInvocationStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    REUSED
}
