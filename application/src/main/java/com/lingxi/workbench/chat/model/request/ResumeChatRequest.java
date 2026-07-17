package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 重新订阅模型生成任务请求。
 *
 * @param assistantMessageId Assistant 消息 ID，同时也是生成任务 ID
 * @param afterSequence 客户端最后成功接收的增量序号
 */
public record ResumeChatRequest(
        @NotBlank(message = "回答消息ID不能为空")
        @Pattern(regexp = "^[1-9]\\d*$", message = "回答消息ID格式不正确")
        String assistantMessageId,
        @Min(value = 0, message = "增量序号不能小于0")
        long afterSequence) {}
