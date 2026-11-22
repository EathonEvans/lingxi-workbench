package com.lingxi.workbench.agentruntime.plan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 提供给后续步骤和最终综合的受限步骤结果。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlanStepResult {

    private final String stepId;
    private final String name;
    private final String capabilityCode;
    private final String content;
}
