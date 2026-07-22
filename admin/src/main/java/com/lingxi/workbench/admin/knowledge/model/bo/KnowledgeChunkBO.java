package com.lingxi.workbench.admin.knowledge.model.bo;

/** 文档分块结果；当前仅用于手动分块确认，尚未持久化。 */
public record KnowledgeChunkBO(
        int chunkNo,
        String content,
        int tokenCount,
        String sectionTitle) {
}
