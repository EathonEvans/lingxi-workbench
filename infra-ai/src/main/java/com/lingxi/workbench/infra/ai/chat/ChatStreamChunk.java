package com.lingxi.workbench.infra.ai.chat;

/**
 * 模型流式增量输出。
 *
 * @param content 文本增量
 */
public record ChatStreamChunk(String content) {
}
