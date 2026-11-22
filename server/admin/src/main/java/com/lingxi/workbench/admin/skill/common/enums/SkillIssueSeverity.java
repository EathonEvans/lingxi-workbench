package com.lingxi.workbench.admin.skill.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Skill 校验问题级别。 */
@Getter
@RequiredArgsConstructor
public enum SkillIssueSeverity {

    ERROR("ERROR", "阻断"),
    WARNING("WARNING", "警告");

    private final String code;
    private final String describe;
}
