package com.lingxi.workbench.admin.knowledge.model.bo;

/**
 * 文档解析后的内存分块结果，完成Embedding后与向量统一持久化。
 *
 * @param chunkNo      分块顺序号，从1开始
 * @param content      分块正文内容
 * @param tokenCount   分块Token数量
 * @param sectionTitle 所属章节标题
 */
public record DocChunkBO(int chunkNo, String content, int tokenCount, String sectionTitle) {
}
