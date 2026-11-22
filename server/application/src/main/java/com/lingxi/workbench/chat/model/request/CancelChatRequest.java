package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 取消模型生成任务请求。
 *
 * @param assistantMessageId Assistant 消息 ID，同时也是生成任务 ID
 */
public record CancelChatRequest(
        @NotBlank(message = "回答消息ID不能为空")
        @Pattern(regexp = "^[1-9]\\d*$", message = "回答消息ID格式不正确")
        String assistantMessageId) {}
