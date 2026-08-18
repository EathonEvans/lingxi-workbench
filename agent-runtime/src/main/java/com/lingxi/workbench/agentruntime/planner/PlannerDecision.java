package com.lingxi.workbench.agentruntime.planner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Spring AI typed structured output 直接绑定的 Planner 决策。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerDecision {

    @JsonProperty(required = true)
    private PlannerDecisionAction action;
    @JsonProperty(required = false)
    private String capabilityCode;
    @JsonProperty(required = false)
    private PlannerPlan plan;
    @JsonProperty(required = false)
    private PlannerClarification clarification;
}
