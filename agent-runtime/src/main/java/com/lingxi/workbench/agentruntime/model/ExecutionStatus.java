package com.lingxi.workbench.agentruntime.model;

/** 当前 Agent Runtime 调用的明确状态。 */
public enum ExecutionStatus {
    RUNNING,
    INTERRUPTED,
    RECONCILIATION_PENDING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELLED
}
