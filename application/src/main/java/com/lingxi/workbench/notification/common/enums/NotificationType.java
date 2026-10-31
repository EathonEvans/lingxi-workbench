package com.lingxi.workbench.notification.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 站内通知类型。
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    /**
     * 工具或计划等待用户确认。
     */
    HITL_PENDING("HITL_PENDING", "待确认操作"),
    /**
     * 定时触发器主动运行的结果汇报。
     */
    RUN_RESULT("RUN_RESULT", "任务结果");

    /**
     * 稳定编码。
     */
    @EnumValue
    private final String code;

    /**
     * 中文说明。
     */
    private final String describe;
}
