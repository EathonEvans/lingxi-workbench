package com.lingxi.workbench.agentruntime.event;

/** Agent Runtime 对外发布的统一生命周期事件类型。 */
public enum ExecutionEventType {
    RUN_STARTED,
    AGENT_STARTED,
    AGENT_COMPLETED,
    PLANNING,
    PLAN_CREATED,
    REPLANNING,
    MODEL_STARTED,
    MODEL_COMPLETED,
    SKILL_ACTIVATED,
    TOOL_STARTED,
    TOOL_REUSED,
    TOOL_COMPLETED,
    TOOL_FAILED,
    HUMAN_REQUIRED,
    HUMAN_RESOLVED,
    FINALIZING,
    RUN_COMPLETED,
    RUN_PARTIAL,
    RUN_RECONCILIATION_PENDING,
    RUN_FAILED,
    RUN_CANCELLED
}
