package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 文档分块与向量化任务数据库实体。
 *
 * <p>每个任务记录一次完整的解析→分块→向量化处理流水线状态，
 * 支持版本化管理，同一文档可多次执行生成不同版本。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_chunk_task")
public class KnowledgeChunkTaskEntity extends TimeEntity {
    /** 任务主键。 */
    private Long id;
    /** 所属知识库 ID。 */
    private Long knowledgeBaseId;
    /** 所属文档 ID。 */
    private Long documentId;
    /** 本次执行使用的 Embedding 模型实例 ID。 */
    private Long embeddingModelInstanceId;
    /** 任务版本号，同一文档按提交顺序递增。 */
    private Integer versionNo;
    /** 分块策略。 */
    private KnowledgeChunkStrategy strategy;
    /** Chunk 目标 Token 数。 */
    private Integer chunkSize;
    /** Chunk 重叠 Token 数。 */
    private Integer chunkOverlap;
    /** 任务当前状态。 */
    private KnowledgeChunkTaskStatus status;
    /** 处理进度百分比，0~100。 */
    private Integer progress;
    /** 生成的 Chunk 总数。 */
    private Integer chunkCount;
    /** 已完成向量化的 Chunk 数。 */
    private Integer vectorizedCount;
    /** 所有 Chunk 的 Token 总和。 */
    private Long totalTokens;
    /** 已执行的重试次数。 */
    private Integer retryCount;
    /** 失败时的错误码。 */
    private String errorCode;
    /** 失败时的错误描述。 */
    private String errorMessage;
    /** 任务开始执行时间。 */
    private OffsetDateTime startedAt;
    /** 任务完成时间。 */
    private OffsetDateTime finishedAt;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}
