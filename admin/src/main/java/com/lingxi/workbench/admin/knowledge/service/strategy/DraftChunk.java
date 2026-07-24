package com.lingxi.workbench.admin.knowledge.service.strategy;

/**
 * 切片中间结果，包含文本内容和所属章节标题。
 *
 * @param content      切片文本内容
 * @param sectionTitle 所属章节标题路径，无章节时为 null
 */
public record DraftChunk(String content, String sectionTitle) {
}
