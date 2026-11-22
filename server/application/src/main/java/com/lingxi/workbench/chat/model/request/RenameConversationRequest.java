package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改会话标题请求。
 *
 * @param title 新的会话标题
 */
public record RenameConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 100, message = "会话标题不能超过100个字符")
        String title) {}
