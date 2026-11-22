package com.lingxi.workbench.admin.agent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.agent.common.enums.KnowledgeBindingMode;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Agent 发布版本的知识库绑定索引。
 */
@Data
@TableName("lingxi_core.ai_agent_version_knowledge")
public class AgentVersionKnowledgeEntity {
    private Long id;
    private Long agentVersionId;
    private Long knowledgeBaseId;
    private Long embeddingModelInstanceId;
    private Long knowledgeIndexVersionId;
    private KnowledgeBindingMode bindingMode;
    private Integer priority;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
