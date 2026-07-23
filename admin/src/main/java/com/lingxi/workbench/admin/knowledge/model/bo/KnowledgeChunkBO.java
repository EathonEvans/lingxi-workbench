package com.lingxi.workbench.admin.knowledge.model.bo;

/** 文档解析后的内存分块结果，完成Embedding后与向量统一持久化。 */
public record KnowledgeChunkBO(
        int chunkNo,
        String content,
        int tokenCount,
        String sectionTitle) {
}
