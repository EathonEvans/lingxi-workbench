package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 知识库当前内容集合的不可变发布版本。 */
@Data
@TableName("lingxi_core.ai_knowledge_index_version")
public class KnowledgeIndexVersionEntity {
    private Long id;
    private Long knowledgeBaseId;
    private Integer versionNo;
    private Long embeddingModelInstanceId;
    private Integer embeddingDimension;
    private String distanceType;
    private String contentChecksum;
    private Integer chunkCount;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
}
