package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;

import java.util.List;

/** 发布校验结果；valid 时 definition 已锁定全部 AgentVersion。
 *  @param definition 已解析锁定 Agent 版本的 Application 定义
 *  @param issues     逐项校验问题列表
 */
public record ApplicationDefinitionResolution(
        ApplicationDefinition definition,
        List<ApplicationValidationIssueVO> issues) {
    public ApplicationDefinitionResolution {
        issues = List.copyOf(issues);
    }

    public boolean valid() {
        return issues.isEmpty();
    }
}
