package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 知识索引版本冻结的文档分块任务逻辑引用。 */
@Data
@TableName("lingxi_core.ai_knowledge_index_version_task")
public class KnowledgeIndexVersionTaskEntity {
    private Long id;
    private Long indexVersionId;
    private Long documentId;
    private Long chunkTaskId;
    private OffsetDateTime createdAt;
}
