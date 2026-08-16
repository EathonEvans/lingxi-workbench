package com.lingxi.workbench.chat.ops.model.request;

import com.lingxi.workbench.chat.ops.common.enums.ConversationDlqGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 人工重放一条会话记忆死信。 */
public record ReplayConversationDlqRequest(
        @NotNull(message = "消费组不能为空") ConversationDlqGroup group,
        @NotBlank(message = "消息ID不能为空") String msgId) {
}
