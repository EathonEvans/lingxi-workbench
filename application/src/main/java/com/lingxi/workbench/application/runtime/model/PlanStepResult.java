package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 提供给最终综合模型的受限步骤结果。 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlanStepResult {

    private String stepId;
    private String name;
    private String capabilityCode;
    private String content;
}
