package com.lingxi.workbench.admin.skill.model.vo;

import com.lingxi.workbench.admin.skill.common.enums.SkillIssueSeverity;

/**
 * Skill 发布校验问题。
 *
 * @param severity 问题级别
 * @param code     稳定问题编码
 * @param field    对应配置字段
 * @param message  可展示说明
 */
public record SkillValidationIssueVO(SkillIssueSeverity severity, String code, String field, String message) {
}
