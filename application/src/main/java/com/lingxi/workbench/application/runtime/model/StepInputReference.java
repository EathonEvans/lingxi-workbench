package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 计划步骤的命名输入及其可信来源。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StepInputReference {

    private String name;
    private StepInputSource source;
    private String stepId;
    private String value;
}
