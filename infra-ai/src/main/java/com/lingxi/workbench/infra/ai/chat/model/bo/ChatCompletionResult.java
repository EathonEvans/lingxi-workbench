package com.lingxi.workbench.infra.ai.chat.model.bo;

/**
 * 非流式模型调用结果。
 *
 * @param content 完整文本
 * @param usage   当前模型响应的 Token 用量
 */
public record ChatCompletionResult(String content, TokenUsage usage) {

    public ChatCompletionResult {
        usage = java.util.Objects.requireNonNullElseGet(usage, TokenUsage::empty);
    }

}
