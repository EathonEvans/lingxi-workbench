package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 单次计划步骤尝试的持久化状态。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionStepStatus {
    PENDING("PENDING", "等待中"),
    RUNNING("RUNNING", "执行中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消"),
    INTERRUPTED("INTERRUPTED", "等待人工确认"),
    REUSED("REUSED", "已复用"),
    SKIPPED("SKIPPED", "已跳过");

    @EnumValue
    private final String code;
    private final String describe;
}
