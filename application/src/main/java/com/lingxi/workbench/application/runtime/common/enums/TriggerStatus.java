package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 定时触发器状态。 */
@Getter
@RequiredArgsConstructor
public enum TriggerStatus {

    /** 调度中。 */
    ACTIVE("ACTIVE", "调度中"),
    /** 已暂停（用户手动或连续失败自动暂停）。 */
    PAUSED("PAUSED", "已暂停"),
    /** 已归档，不再展示和调度。 */
    ARCHIVED("ARCHIVED", "已归档");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;

    /** 中文说明。 */
    private final String describe;
}
