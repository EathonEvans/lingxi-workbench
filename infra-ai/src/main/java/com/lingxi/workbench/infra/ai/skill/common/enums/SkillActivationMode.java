package com.lingxi.workbench.infra.ai.skill.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Skill 激活方式。 */
@Getter
@RequiredArgsConstructor
public enum SkillActivationMode {

    EXPLICIT_ONLY("EXPLICIT_ONLY", "仅显式启用"),
    MODEL_ROUTED("MODEL_ROUTED", "模型按需选择"),
    ALWAYS_ON("ALWAYS_ON", "每轮自动启用");

    private final String code;
    private final String describe;
}
