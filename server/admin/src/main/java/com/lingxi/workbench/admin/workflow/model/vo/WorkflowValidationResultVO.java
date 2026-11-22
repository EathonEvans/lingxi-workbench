package com.lingxi.workbench.admin.workflow.model.vo;

import java.util.List;

/**
 * Workflow 草稿校验结果。
 *
 * @param valid  是否可以发布
 * @param issues 校验问题
 */
public record WorkflowValidationResultVO(boolean valid, List<WorkflowValidationIssueVO> issues) {

    public WorkflowValidationResultVO {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
