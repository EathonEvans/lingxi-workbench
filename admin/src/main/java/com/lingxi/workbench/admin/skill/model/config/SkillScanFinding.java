package com.lingxi.workbench.admin.skill.model.config;

import com.lingxi.workbench.admin.skill.common.enums.SkillIssueSeverity;

/**
 * Skill 发布扫描发现。
 *
 * @param severity 风险级别
 * @param code     稳定风险编码
 * @param message  可展示的风险说明
 */
public record SkillScanFinding(SkillIssueSeverity severity, String code, String message) {
}
