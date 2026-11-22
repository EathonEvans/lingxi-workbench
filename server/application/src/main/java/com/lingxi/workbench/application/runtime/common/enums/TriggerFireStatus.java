package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 触发器单次到期触发的生命周期状态。 */
@Getter
@RequiredArgsConstructor
public enum TriggerFireStatus {

    /** 已抢占并投递执行。 */
    FIRED("FIRED", "已触发"),
    /** 执行中。 */
    RUNNING("RUNNING", "执行中"),
    /** 满足触发条件但被跳过，记录原因后等待下个周期。 */
    SKIPPED("SKIPPED", "已跳过"),
    /** 执行成功。 */
    SUCCEEDED("SUCCEEDED", "执行成功"),
    /** 执行失败。 */
    FAILED("FAILED", "执行失败"),
    /** 用户主动取消。 */
    CANCELLED("CANCELLED", "已取消"),
    /** 执行超时或实例失联，由对账收敛。 */
    EXPIRED("EXPIRED", "已过期");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;

    /** 中文说明。 */
    private final String describe;
}
