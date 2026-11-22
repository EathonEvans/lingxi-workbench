package com.lingxi.workbench.admin.agent.model.vo;

import java.util.List;

/** Agent 发布前校验结果。
 *  @param valid  整体校验是否通过
 *  @param issues 逐项校验问题列表
 */
public record AgentValidationResultVO(boolean valid, List<AgentValidationIssueVO> issues) {
    public AgentValidationResultVO {
        issues = List.copyOf(issues);
    }
}
