package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建模型生成任务请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRequest {
    /**
     * 会话id
     */
    @NotBlank(message = "会话ID不能为空")
    @Pattern(regexp = "^[1-9]\\d*$", message = "会话ID格式不正确")
    String conversationId;
    /**
     * 客户端消息id
     */
    @NotBlank(message = "客户端消息ID不能为空")
    @Size(max = 64, message = "客户端消息ID不能超过64个字符")
    String clientMessageId;
    /**
     * 用户问题
     */
    @NotBlank(message = "用户输入不能为空")
    @Size(max = 32_000, message = "用户输入长度不能超过32000个字符")
    String query;
    /**
     * AI 应用稳定编码。
     *
     * <p>每次对话都必须归属于一个已发布 Application。普通聊天也应由 Application
     * 配置的默认 Agent 承担，不能通过缺少应用编码隐式进入平台默认模型。</p>
     */
    @NotBlank(message = "AI应用编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "AI应用编码格式不正确")
    String applicationCode;

    /**
     * agent标识
     */
    String agentCode;
}
