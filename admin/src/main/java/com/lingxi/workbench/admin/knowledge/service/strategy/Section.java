package com.lingxi.workbench.admin.knowledge.service.strategy;

/**
 * Markdown 文档片段，包含章节标题和文本内容。
 *
 * @param title   层级化的章节标题路径，无标题时为 null
 * @param content 该章节下的文本内容
 */
record Section(String title, String content) {
}
