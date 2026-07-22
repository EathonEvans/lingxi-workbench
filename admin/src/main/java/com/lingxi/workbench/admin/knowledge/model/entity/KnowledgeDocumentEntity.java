package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 知识库文档及解析结果数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_document")
public class KnowledgeDocumentEntity extends TimeEntity {
    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private KnowledgeDocumentSourceType sourceType;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String objectKey;
    private String contentHash;
    private KnowledgeDocumentStatus status;
    private Integer pageCount;
    private String parsedContent;
    private String parsedContentHash;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime parsedAt;
    private Long createdBy;
    private Long updatedBy;
}
