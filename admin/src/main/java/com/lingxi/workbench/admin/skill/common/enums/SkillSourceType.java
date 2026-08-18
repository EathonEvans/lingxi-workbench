package com.lingxi.workbench.admin.skill.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Skill 来源类型。 */
@Getter
@RequiredArgsConstructor
public enum SkillSourceType {

    SYSTEM_PRESET("SYSTEM_PRESET", "平台预设"),
    USER_CREATED("USER_CREATED", "用户创建"),
    THIRD_PARTY_IMPORTED("THIRD_PARTY_IMPORTED", "第三方导入");

    @EnumValue
    private final String code;
    private final String describe;
}
