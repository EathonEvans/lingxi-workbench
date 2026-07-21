package com.lingxi.workbench.admin.knowledge.model.bo;

/** 文档切片结果；当前仅在解析任务内存中流转，尚未持久化。 */
public record KnowledgeChunkBO(
        int chunkNo,
        String content,
        String contentHash,
        int tokenCount,
        String sectionTitle) {
}
