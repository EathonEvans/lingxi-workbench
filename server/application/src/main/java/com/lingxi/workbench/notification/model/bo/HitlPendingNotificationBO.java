package com.lingxi.workbench.notification.model.bo;

import lombok.Builder;

/**
 * 创建 HITL 待确认通知的业务入参。
 *
 * @param userId            归属用户主键
 * @param conversationId    关联会话主键
 * @param assistantMessageId 关联 Assistant 消息主键
 * @param interactionId     HITL 交互会话主键
 * @param title             通知标题（交互标题）
 * @param summary           通知摘要（交互说明，不含工具敏感参数）
 */
@Builder
public record HitlPendingNotificationBO(Long userId, Long conversationId, Long assistantMessageId,
                                        Long interactionId, String title, String summary) {
}
