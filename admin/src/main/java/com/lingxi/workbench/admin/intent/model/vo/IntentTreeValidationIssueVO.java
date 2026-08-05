package com.lingxi.workbench.admin.intent.model.vo;

/**
 * 意图树发布校验问题。
 *
 * @param code 稳定问题编码
 * @param field 问题字段或节点定位
 * @param message 面向管理员的说明
 */
public record IntentTreeValidationIssueVO(String code, String field, String message) {
}
