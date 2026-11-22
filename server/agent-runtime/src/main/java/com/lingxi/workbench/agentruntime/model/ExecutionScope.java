package com.lingxi.workbench.agentruntime.model;

/** 区分整次 Run 与 Supervisor 内部隔离能力调用，避免子 Agent 提前结束父 Run。 */
public enum ExecutionScope {
    RUN,
    INVOCATION;

    public boolean isRun() {
        return this == RUN;
    }
}
