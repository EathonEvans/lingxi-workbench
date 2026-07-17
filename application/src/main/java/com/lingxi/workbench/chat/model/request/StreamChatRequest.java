package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 流式对话请求。
 *
 * @param conversationId 会话 ID
 * @param clientMessageId 前端生成的消息幂等标识
 * @param prompt 用户输入
 */
public record StreamChatRequest(
        @NotBlank(message = "会话ID不能为空")
        @Pattern(regexp = "^[1-9]\\d*$", message = "会话ID格式不正确")
        String conversationId,
        @NotBlank(message = "客户端消息ID不能为空")
        @Size(max = 64, message = "客户端消息ID不能超过64个字符")
        String clientMessageId,
        @NotBlank(message = "用户输入不能为空")
        @Size(max = 32_000, message = "用户输入长度不能超过32000个字符")
        String prompt) {}
