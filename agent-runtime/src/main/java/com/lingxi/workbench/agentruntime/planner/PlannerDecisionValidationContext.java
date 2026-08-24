package com.lingxi.workbench.agentruntime.planner;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;

/** Planner 决策校验使用当前规划轮次真实可用的能力与委派预算。 */
@Getter
@Builder
public class PlannerDecisionValidationContext {

    private Map<String, Integer> remainingCapabilityCalls;
    private int maxSteps;
    private int remainingDelegations;

    public Map<String, Integer> remainingCapabilityCalls() {
        return Map.copyOf(Objects.requireNonNullElseGet(remainingCapabilityCalls, Map::of));
    }
}
