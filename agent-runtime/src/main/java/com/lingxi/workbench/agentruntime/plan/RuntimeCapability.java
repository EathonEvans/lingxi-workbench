package com.lingxi.workbench.agentruntime.plan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Application 从已发布版本投影给 Runtime 的最小能力事实。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeCapability {

    private final String code;
    private final String name;
    private final RuntimeCapabilityType type;
    private final RuntimeCapabilityRisk risk;
    private final int maxCalls;
    private final int resultTokenBudget;
}
