package com.lingxi.workbench.infra.ai.skill.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Skill 安全治理状态。 */
@Getter
@RequiredArgsConstructor
public enum SkillSecurityStatus {

    NORMAL("NORMAL", "正常"),
    QUARANTINED("QUARANTINED", "隔离中"),
    REVOKED("REVOKED", "已撤销");

    @EnumValue
    private final String code;
    private final String describe;
}
