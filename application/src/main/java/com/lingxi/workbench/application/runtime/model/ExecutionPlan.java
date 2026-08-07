package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 一次 Planner 决策中的有限串行计划。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionPlan {

    private String title;
    private List<PlanStep> steps;

    public List<PlanStep> steps() {
        return steps == null ? List.of() : List.copyOf(steps);
    }
}
