package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库文档及其处理配置数据库实体。 */
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
    /** 文档级分块策略，重新执行时默认沿用上次配置。 */
    private KnowledgeChunkStrategy chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    /** 当前生效版本的分块数量，供列表统计使用。 */
    private Integer chunkCount;
    private String errorCode;
    private String errorMessage;
    /** 当前已完成向量化并正式生效的分块任务。 */
    private Long activeChunkTaskId;
    private Long createdBy;
    private Long updatedBy;
}
