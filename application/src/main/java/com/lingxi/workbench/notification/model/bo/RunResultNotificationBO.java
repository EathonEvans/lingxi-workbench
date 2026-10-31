package com.lingxi.workbench.notification.model.bo;

import lombok.Builder;

/**
 * 创建定时触发器结果通知的业务入参。
 *
 * @param userId         归属用户主键
 * @param triggerId      归属触发器主键
 * @param conversationId 触发器专属会话主键
 * @param messageId      本次运行的 Assistant 消息主键，可空（失败无产出时）
 * @param title          通知标题（触发器名称与结果状态）
 * @param summary        通知摘要
 */
@Builder
public record RunResultNotificationBO(Long userId, Long triggerId, Long conversationId,
                                      Long messageId, String title, String summary) {
}
