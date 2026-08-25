package com.lingxi.workbench.infra.ai.tool.model;

/** 外部 Tool 按幂等键返回的对账结论。 */
public enum AgentToolReconciliationStatus {
    SUCCEEDED,
    NOT_APPLIED,
    PENDING,
    COMPENSATION_REQUIRED
}
