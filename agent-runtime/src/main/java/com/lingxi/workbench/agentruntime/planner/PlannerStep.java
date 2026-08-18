package com.lingxi.workbench.agentruntime.planner;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Planner 计划中一个只引用已授权能力的步骤。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerStep {

    private String id;
    private String name;
    private String capabilityCode;
    private List<String> dependsOn;

    public List<String> dependsOn() {
        return dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
