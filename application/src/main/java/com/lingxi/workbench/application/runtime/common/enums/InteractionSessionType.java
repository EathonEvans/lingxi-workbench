package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通用运行时交互会话类型。 */
@Getter
@RequiredArgsConstructor
public enum InteractionSessionType {
    CLARIFICATION("CLARIFICATION", "信息澄清"),
    ACTION_APPROVAL("ACTION_APPROVAL", "动作审批"),
    PLAN_REVIEW("PLAN_REVIEW", "计划审阅");

    @EnumValue
    private final String code;
    private final String describe;
}
