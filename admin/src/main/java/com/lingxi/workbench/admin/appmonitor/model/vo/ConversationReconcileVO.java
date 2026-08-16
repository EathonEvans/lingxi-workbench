package com.lingxi.workbench.admin.appmonitor.model.vo;

/**
 * @param interruptedCount 修复的僵尸生成任务数
 * @param republishedCount 兜底重发的记忆事件数
 */
public record ConversationReconcileVO(int interruptedCount, int republishedCount) {
}
