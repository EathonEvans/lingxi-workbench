package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.common.enums.DocChunkStrategy;

import java.util.List;

/**
 * 手动执行文档分块结果；当前仅用于用户确认，不代表Chunk已经持久化。
 *
 * @param documentId 文档ID
 * @param documentName 文档名称
 * @param strategy 分块策略
 * @param chunkSize 单个Chunk的目标Token数
 * @param chunkOverlap 相邻Chunk的重叠Token数
 * @param chunkCount Chunk总数
 * @param totalTokens 所有Chunk的Token数量之和
 * @param chunks Chunk预览列表
 */
public record KnowledgeChunkResultVO(
        String documentId,
        String documentName,
        DocChunkStrategy strategy,
        int chunkSize,
        int chunkOverlap,
        int chunkCount,
        long totalTokens,
        List<KnowledgeChunkVO> chunks) {}
