package com.lingxi.workbench.admin.knowledge.model.vo;

/**
 * 单个文档Chunk预览。
 *
 * @param chunkNo Chunk顺序号
 * @param content Chunk正文
 * @param tokenCount Token数量
 * @param sectionTitle 所属标题路径
 */
public record KnowledgeChunkVO(
        int chunkNo,
        String content,
        int tokenCount,
        String sectionTitle) {}
