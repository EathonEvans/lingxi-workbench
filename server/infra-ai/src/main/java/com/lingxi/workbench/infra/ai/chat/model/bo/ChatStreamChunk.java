package com.lingxi.workbench.infra.ai.chat.model.bo;

/**
 * 模型流式增量输出。
 *
 * <p>部分 OpenAI 兼容服务会在最后一个无文本增量中返回 Token 用量，
 * 因此用量字段不能依赖文本是否为空。</p>
 *
 * @param content 文本增量
 * @param usage   当前模型响应携带的 Token 用量
 */
public record ChatStreamChunk(String content, TokenUsage usage) {

    public ChatStreamChunk {
        usage = java.util.Objects.requireNonNullElseGet(usage, TokenUsage::empty);
    }

    /** 创建不包含 Token 用量的普通文本增量。 */
    public ChatStreamChunk(String content) {
        this(content, TokenUsage.empty());
    }

    /** 判断当前增量是否携带模型返回的 Token 用量。 */
    public boolean hasUsage() {
        return usage.hasUsage();
    }
}
