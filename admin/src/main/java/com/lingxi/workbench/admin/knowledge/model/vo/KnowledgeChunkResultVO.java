package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;

import java.time.OffsetDateTime;

/**
 * 文档分块与向量化任务状态。
 *
 * @param documentId 文档ID
 * @param documentName 文档名称
 * @param strategy 分块策略
 * @param chunkSize 单个Chunk的目标Token数
 * @param chunkOverlap 相邻Chunk的重叠Token数
 * @param chunkCount Chunk总数
 * @param totalTokens 所有Chunk的Token数量之和
 * @param retryCount 已执行的自动重试次数
 */
public record KnowledgeChunkResultVO(
        String taskId,
        String documentId,
        String documentName,
        KnowledgeChunkStrategy strategy,
        int chunkSize,
        int chunkOverlap,
        KnowledgeChunkTaskStatus status,
        int progress,
        int chunkCount,
        int vectorizedCount,
        long totalTokens,
        int retryCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt) {}
