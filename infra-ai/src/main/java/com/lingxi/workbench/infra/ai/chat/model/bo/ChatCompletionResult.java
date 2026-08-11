package com.lingxi.workbench.infra.ai.chat.model.bo;

/**
 * 非流式模型调用结果。
 *
 * @param content 已按调用类型解析的完整内容
 * @param usage   当前模型响应的 Token 用量
 */
public record ChatCompletionResult<T>(T content, TokenUsage usage) {

    public ChatCompletionResult {
        usage = java.util.Objects.requireNonNullElseGet(usage, TokenUsage::empty);
    }

}
