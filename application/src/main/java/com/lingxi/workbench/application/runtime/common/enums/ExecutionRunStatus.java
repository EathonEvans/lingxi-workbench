package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Application 顶级执行运行状态。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionRunStatus {
    RUNNING("RUNNING"),
    WAITING_HUMAN("WAITING_HUMAN"),
    RESUMING("RESUMING"),
    SUCCEEDED("SUCCEEDED"),
    PARTIAL("PARTIAL"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED"),
    EXPIRED("EXPIRED"),
    RECONCILIATION_PENDING("RECONCILIATION_PENDING");

    @EnumValue
    private final String code;
}
