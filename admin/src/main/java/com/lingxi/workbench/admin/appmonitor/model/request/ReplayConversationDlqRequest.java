package com.lingxi.workbench.admin.appmonitor.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 人工重放一条会话记忆死信。 */
public record ReplayConversationDlqRequest(
        @NotBlank(message = "消费组不能为空")
        @Pattern(regexp = "SUMMARY|PROFILE|INTERACTION", message = "消费组不受支持") String group,
        @NotBlank(message = "消息ID不能为空") String msgId) {
}
