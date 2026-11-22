package com.lingxi.workbench.admin.application.model.vo;

import java.util.List;

/** Application 发布前校验结果。
 *  @param valid  整体校验是否通过
 *  @param issues 逐项校验问题列表
 */
public record ApplicationValidationResultVO(
        boolean valid,
        List<ApplicationValidationIssueVO> issues) {
    public ApplicationValidationResultVO {
        issues = List.copyOf(issues);
    }
}
