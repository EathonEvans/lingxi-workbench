package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkMetadata;

/**
 * 单个文档Chunk预览。
 *
 * @param chunkNo Chunk顺序号
 * @param content Chunk正文
 * @param charCount 字符数量
 * @param tokenCount Token数量
 * @param metadata 检索上下文元数据
 */
public record KnowledgeChunkVO(
        String id,
        int chunkNo,
        String content,
        int charCount,
        int tokenCount,
        KnowledgeChunkMetadata metadata) {}
