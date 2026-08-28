package com.lingxi.workbench.admin.evaluation.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 评测任务生命周期。 */
@Getter
@RequiredArgsConstructor
public enum EvaluationRunStatus {

    PENDING("PENDING", "等待执行"),
    RUNNING("RUNNING", "执行中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "执行失败"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    private final String code;
    private final String describe;
}
