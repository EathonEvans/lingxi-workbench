package com.lingxi.workbench.chat.model.vo;

/**
 * SSE 对话增量数据。
 *
 * @param content 文本增量
 */
public record ChatChunkVO(String content) {
}
