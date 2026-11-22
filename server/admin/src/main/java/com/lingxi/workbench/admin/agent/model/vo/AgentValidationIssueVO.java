package com.lingxi.workbench.admin.agent.model.vo;

/** Agent 发布前校验问题。
 *  @param code    校验错误码，标识校验项
 *  @param message 校验错误描述
 */
public record AgentValidationIssueVO(String code, String message) {
}
