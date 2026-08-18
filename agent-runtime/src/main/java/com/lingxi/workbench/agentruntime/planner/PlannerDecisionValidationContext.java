package com.lingxi.workbench.agentruntime.planner;

import lombok.Builder;
import lombok.Getter;

import java.util.Objects;
import java.util.Set;

/** Planner 决策校验只依赖发布版本冻结的能力白名单和步骤上限。 */
@Getter
@Builder
public class PlannerDecisionValidationContext {

    private Set<String> authorizedCapabilityCodes;
    private int maxSteps;

    public Set<String> authorizedCapabilityCodes() {
        return Set.copyOf(Objects.requireNonNullElseGet(authorizedCapabilityCodes, Set::of));
    }
}
