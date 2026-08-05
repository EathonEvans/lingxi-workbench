package com.lingxi.workbench.admin.intent.model.vo;

import java.util.List;

/**
 * 意图树发布校验结果。
 *
 * @param valid 是否通过发布校验
 * @param issues 校验问题列表
 */
public record IntentTreeValidationResultVO(boolean valid, List<IntentTreeValidationIssueVO> issues) {

    public IntentTreeValidationResultVO {
        issues = List.copyOf(issues);
    }
}
