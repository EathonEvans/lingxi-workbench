package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 不可变执行检查点生命周期。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionCheckpointStatus {
    ACTIVE("ACTIVE"),
    CONSUMED("CONSUMED"),
    SUPERSEDED("SUPERSEDED"),
    EXPIRED("EXPIRED");

    @EnumValue
    private final String code;
}
