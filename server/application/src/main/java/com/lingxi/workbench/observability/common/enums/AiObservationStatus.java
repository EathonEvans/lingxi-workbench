package com.lingxi.workbench.observability.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 观测事件执行状态。 */
@Getter
@RequiredArgsConstructor
public enum AiObservationStatus {
    SUCCESS("SUCCESS", "成功"),
    ERROR("ERROR", "失败");

    @EnumValue
    private final String code;
    private final String describe;
}
