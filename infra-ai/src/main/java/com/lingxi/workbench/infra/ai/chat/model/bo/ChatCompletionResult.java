package com.lingxi.workbench.infra.ai.chat.model.bo;

/**
 * 非流式模型调用结果。
 *
 * @param content      完整文本
 * @param inputTokens  输入 Token 数
 * @param outputTokens 输出 Token 数
 * @param totalTokens  总 Token 数
 */
public record ChatCompletionResult(
        String content,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens) {
}
