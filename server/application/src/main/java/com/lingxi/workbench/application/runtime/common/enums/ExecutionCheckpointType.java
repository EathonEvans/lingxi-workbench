package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可恢复执行检查点类型。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionCheckpointType {
    TOOL_APPROVAL("TOOL_APPROVAL"),
    PLAN_REVIEW("PLAN_REVIEW");

    @EnumValue
    private final String code;
}
