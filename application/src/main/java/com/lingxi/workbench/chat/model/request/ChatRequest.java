package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * 创建模型生成任务请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRequest {
    /**
     * 已落库会话主键；新会话首次发送时为空。
     */
    @Pattern(regexp = "^[1-9]\\d*$", message = "会话ID格式不正确")
    String conversationId;
    /**
     * 前端本地会话标识，仅在首次发送创建会话时使用。
     */
    @Size(max = 64, message = "客户端会话ID不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "客户端会话ID格式不正确")
    String clientConversationId;
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
     * 上一轮 clarification(澄清) 事件的可选结构化响应。
     */
    @Valid
    ClarificationResponseRequest clarificationResponse;

    /** 本轮显式启用的 EXPLICIT_ONLY Skill code。 */
    @Size(max = 3, message = "单轮最多显式启用3个Skill")
    List<@Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "Skill编码格式不正确") String> skillCodes;

    public String getQuery() {
        return query.trim();
    }

    public List<String> getSkillCodes() {
        return List.copyOf(Objects.requireNonNullElseGet(skillCodes, List::of));
    }

    /** 新会话和已有会话入口必须且只能选择一个。 */
    @AssertTrue(message = "会话ID与客户端会话ID必须且只能提供一个")
    public boolean isConversationTargetValid() {
        boolean hasConversationId = conversationId != null && !conversationId.isBlank();
        boolean hasClientConversationId = clientConversationId != null && !clientConversationId.isBlank();
        return hasConversationId != hasClientConversationId;
    }
}
