package com.lingxi.workbench.infra.ai.skill.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Skill 本轮实际激活原因。 */
@Getter
@RequiredArgsConstructor
public enum SkillActivationReason {

    ALWAYS_ON("ALWAYS_ON", "平台预设自动启用"),
    EXPLICIT("EXPLICIT", "用户显式启用"),
    MODEL_ROUTED("MODEL_ROUTED", "模型按需选择");

    private final String code;
    private final String describe;
}
