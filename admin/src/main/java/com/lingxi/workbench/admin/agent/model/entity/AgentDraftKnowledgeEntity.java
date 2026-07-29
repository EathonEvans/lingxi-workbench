package com.lingxi.workbench.admin.agent.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.agent.common.enums.KnowledgeBindingMode;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 草稿的知识库绑定。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_agent_draft_knowledge")
public class AgentDraftKnowledgeEntity extends TimeEntity {
    private Long id;
    private Long agentDraftId;
    private Long knowledgeBaseId;
    private Long embeddingModelInstanceId;
    private KnowledgeBindingMode bindingMode;
    private Integer priority;
}
