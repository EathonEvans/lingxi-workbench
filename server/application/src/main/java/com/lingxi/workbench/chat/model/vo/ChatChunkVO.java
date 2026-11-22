package com.lingxi.workbench.chat.model.vo;

/**
 * SSE 对话增量数据。
 *
 * @param sequence 当前任务内单调递增的增量序号
 * @param content 文本增量
 */
public record ChatChunkVO(long sequence, String content) {}
