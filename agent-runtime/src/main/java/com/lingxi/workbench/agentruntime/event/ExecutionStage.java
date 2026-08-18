package com.lingxi.workbench.agentruntime.event;

/** Agent Runtime 生命周期中可安全展示和持久化的阶段。 */
public enum ExecutionStage {
    RUNTIME,
    AGENT,
    PLAN,
    MODEL,
    SKILL,
    TOOL,
    HUMAN,
    TERMINAL
}
