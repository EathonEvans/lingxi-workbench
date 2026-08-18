package com.lingxi.workbench.agentruntime.planner;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Supervisor 一次决策中的有限串行计划。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerPlan {

    private String title;
    private List<PlannerStep> steps;

    public List<PlannerStep> steps() {
        return steps == null ? List.of() : List.copyOf(steps);
    }
}
