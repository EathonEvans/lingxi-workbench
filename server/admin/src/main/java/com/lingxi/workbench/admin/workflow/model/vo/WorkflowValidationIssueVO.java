package com.lingxi.workbench.admin.workflow.model.vo;

/**
 * Workflow 校验问题。
 *
 * @param code    稳定问题编码
 * @param nodeId  关联节点 ID，无节点归属时为空
 * @param message 可操作的错误说明
 */
public record WorkflowValidationIssueVO(String code, String nodeId, String message) {
}
