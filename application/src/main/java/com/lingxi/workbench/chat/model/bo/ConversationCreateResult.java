package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.model.entity.ConversationEntity;

/**
 * @param conversation 已创建或并发命中的会话
 * @param created      本请求是否完成了首次插入
 */
public record ConversationCreateResult(ConversationEntity conversation, boolean created) {
}
