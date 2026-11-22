package com.lingxi.workbench.agentruntime.plan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** AgentScope 任务清单中一次已发布能力委派的稳定投影。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeTaskStep {

    private final String id;
    private final String name;
    private final String capabilityCode;
}
