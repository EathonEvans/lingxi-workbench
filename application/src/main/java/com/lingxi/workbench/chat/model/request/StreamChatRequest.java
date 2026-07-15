package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 流式对话请求。
 *
 * @param prompt 用户输入
 */
public record StreamChatRequest(
        @NotBlank(message = "用户输入不能为空")
        @Size(max = 32_000, message = "用户输入长度不能超过32000个字符")
        String prompt
) {
}
