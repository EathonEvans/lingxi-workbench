package com.lingxi.workbench.admin.application.model.vo;

/** Application 发布前校验问题。field 使用节点ID或 definition，便于画布定位问题。
 *  @param code    校验错误码
 *  @param field   问题定位字段，如 nodeId 或 definition
 *  @param message 校验错误描述
 */
public record ApplicationValidationIssueVO(String code, String field, String message) {
}
