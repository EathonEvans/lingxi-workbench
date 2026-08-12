package com.lingxi.workbench.infra.ai.chat.model.bo;

/**
 * 非流式模型调用结果。
 *
 * @param content             已按调用类型解析的完整内容
 * @param usage               当前模型响应的 Token 用量
 * @param finishReason        供应商返回的生成终止原因
 * @param reasoningCharacters 推理内容字符数，仅用于判断空正文是否由推理输出占满预算
 */
public record ChatCompletionResult<T>(T content, TokenUsage usage, String finishReason, int reasoningCharacters) {

    public ChatCompletionResult(T content, TokenUsage usage) {
        this(content, usage, null, 0);
    }

    public ChatCompletionResult {
        usage = java.util.Objects.requireNonNullElseGet(usage, TokenUsage::empty);
        reasoningCharacters = Math.max(reasoningCharacters, 0);
    }

}
