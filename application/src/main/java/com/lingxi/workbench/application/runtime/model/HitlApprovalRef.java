package com.lingxi.workbench.application.runtime.model;

/** 已通过事务决策并绑定到当前恢复运行的精确审批引用。 */
public record HitlApprovalRef(Long interactionId, String fingerprint) {
}
