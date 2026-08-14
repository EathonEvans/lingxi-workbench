package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 计划中的单个能力调用步骤。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlanStep {

    private String id;
    private String name;
    private String capabilityCode;
    private List<String> dependsOn;

    public List<String> dependsOn() {
        return dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
