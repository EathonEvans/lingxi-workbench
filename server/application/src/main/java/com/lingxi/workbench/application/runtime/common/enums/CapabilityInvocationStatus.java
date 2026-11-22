package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 外部能力调用意图与实际执行状态。 */
@Getter
@RequiredArgsConstructor
public enum CapabilityInvocationStatus {
    PENDING_APPROVAL("PENDING_APPROVAL"),
    APPROVED("APPROVED"),
    RUNNING("RUNNING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED"),
    RECONCILIATION_PENDING("RECONCILIATION_PENDING"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED"),
    EXPIRED("EXPIRED");

    @EnumValue
    private final String code;
}
