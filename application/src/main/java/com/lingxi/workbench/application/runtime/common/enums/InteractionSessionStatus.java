package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通用运行时交互会话状态。 */
@Getter
@RequiredArgsConstructor
public enum InteractionSessionStatus {
    PENDING("PENDING", "待处理"),
    RESOLVED("RESOLVED", "已解决"),
    REJECTED("REJECTED", "已拒绝"),
    CANCELLED("CANCELLED", "已取消"),
    EXPIRED("EXPIRED", "已过期"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败");

    @EnumValue
    private final String code;
    private final String describe;
}
