package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_base")
public class KnowledgeBaseEntity extends TimeEntity {
    private Long id;
    private String name;
    private String code;
    private String description;
    /** 知识库固定使用的Embedding模型实例。 */
    private Long embeddingModelInstanceId;
    private EnableStatus status;
    private Long createdBy;
    private Long updatedBy;
}
