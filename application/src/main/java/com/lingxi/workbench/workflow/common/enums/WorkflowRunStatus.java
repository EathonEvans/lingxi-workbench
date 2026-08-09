package com.lingxi.workbench.workflow.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Workflow 运行状态。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowRunStatus {
    RUNNING("RUNNING", "执行中"),
    SUCCEEDED("SUCCEEDED", "执行成功"),
    FAILED("FAILED", "执行失败");

    @EnumValue
    private final String code;
    private final String describe;
}
