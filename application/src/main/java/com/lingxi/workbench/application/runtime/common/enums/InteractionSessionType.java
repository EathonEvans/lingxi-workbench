package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通用运行时交互会话类型。 */
@Getter
@RequiredArgsConstructor
public enum InteractionSessionType {
    PLANNER_CLARIFICATION("PLANNER_CLARIFICATION", "Planner信息澄清"),
    RISK_CONFIRMATION("RISK_CONFIRMATION", "高风险操作确认");

    @EnumValue
    private final String code;
    private final String describe;
}
