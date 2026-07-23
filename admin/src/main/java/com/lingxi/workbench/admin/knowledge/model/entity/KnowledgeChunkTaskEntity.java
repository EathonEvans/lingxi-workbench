package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 文档分块与向量化任务数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_chunk_task")
public class KnowledgeChunkTaskEntity extends TimeEntity {
    private Long id;
    private Long knowledgeBaseId;
    private Long documentId;
    private Long embeddingModelInstanceId;
    private Integer versionNo;
    private KnowledgeChunkStrategy strategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private KnowledgeChunkTaskStatus status;
    private Integer progress;
    private Integer chunkCount;
    private Integer vectorizedCount;
    private Long totalTokens;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Long createdBy;
    private Long updatedBy;
}
