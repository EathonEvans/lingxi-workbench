package com.lingxi.workbench.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库数据库实体（只读）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_base")
public class KnowledgeBaseEntity extends TimeEntity {
    private Long id;
    private String name;
    private String code;
    /** 知识库固定使用的 Embedding 模型实例 ID。 */
    private Long embeddingModelInstanceId;
    private String status;
}
