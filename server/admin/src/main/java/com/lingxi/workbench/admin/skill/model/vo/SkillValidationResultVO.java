package com.lingxi.workbench.admin.skill.model.vo;

import java.util.List;

/**
 * Skill 发布校验结果。
 *
 * @param valid  是否允许发布
 * @param issues 校验问题
 */
public record SkillValidationResultVO(boolean valid, List<SkillValidationIssueVO> issues) {

    public SkillValidationResultVO {
        issues = List.copyOf(issues);
    }
}
