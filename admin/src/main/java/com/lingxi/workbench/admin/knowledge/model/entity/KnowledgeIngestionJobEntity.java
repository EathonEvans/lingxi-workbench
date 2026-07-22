package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeJobStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 文档解析任务数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_ingestion_job")
public class KnowledgeIngestionJobEntity extends TimeEntity {
    private Long id;
    private Long knowledgeBaseId;
    private Long documentId;
    private KnowledgeJobStatus status;
    private Integer progress;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Long createdBy;
    private Long updatedBy;
}
