package com.lingxi.workbench.infra.ai.skill.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Skill 主体生命周期状态。 */
@Getter
@RequiredArgsConstructor
public enum SkillLifecycleStatus {

    ACTIVE("ACTIVE", "启用"),
    DISABLED("DISABLED", "停用");

    @EnumValue
    private final String code;
    private final String describe;
}
