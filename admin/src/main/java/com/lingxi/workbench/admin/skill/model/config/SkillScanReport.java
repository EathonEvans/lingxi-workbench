package com.lingxi.workbench.admin.skill.model.config;

import java.util.List;

/**
 * Skill 发布扫描报告。
 *
 * @param scannerVersion 确定性扫描规则版本
 * @param findings       扫描发现
 */
public record SkillScanReport(String scannerVersion, List<SkillScanFinding> findings) {

    public SkillScanReport {
        findings = List.copyOf(findings);
    }
}
