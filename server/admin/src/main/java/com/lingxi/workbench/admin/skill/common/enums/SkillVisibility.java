package com.lingxi.workbench.admin.skill.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Skill 可见范围。 */
@Getter
@RequiredArgsConstructor
public enum SkillVisibility {

    PRIVATE("PRIVATE", "仅创建者可见"),
    SYSTEM("SYSTEM", "平台可见");

    @EnumValue
    private final String code;
    private final String describe;
}
