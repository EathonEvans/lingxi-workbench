package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 文档分块与向量化任务状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeChunkResultVO {
    /**
     * 任务ID
     */
    private String taskId;
    /**
     * 文档ID
     */
    private String documentId;
    /**
     * 文档名称
     */
    private String documentName;
    /**
     * 分块策略
     */
    private KnowledgeChunkStrategy strategy;
    /**
     * 单个Chunk的目标Token数
     */
    private int chunkSize;
    /**
     * 相邻Chunk的重叠Token数
     */
    private int chunkOverlap;
    /**
     * 任务状态
     */
    private KnowledgeChunkTaskStatus status;
    /**
     * 处理进度百分比
     */
    private int progress;
    /**
     * Chunk总数
     */
    private int chunkCount;
    /**
     * 已完成向量化的Chunk数
     */
    private int vectorizedCount;
    /**
     * 所有Chunk的Token数量之和
     */
    private long totalTokens;
    /**
     * 已执行的自动重试次数
     */
    private int retryCount;
    /**
     * 失败时的错误信息
     */
    private String errorMessage;
    /**
     * 任务创建时间
     */
    private OffsetDateTime createdAt;
    /**
     * 任务完成时间
     */
    private OffsetDateTime finishedAt;
}
