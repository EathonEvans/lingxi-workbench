package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Planner 返回的结构化执行决策。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionDecision {

    private ExecutionDecisionAction action;
    private String capabilityCode;
    private ExecutionPlan plan;
    private PlannerClarification clarification;
}
