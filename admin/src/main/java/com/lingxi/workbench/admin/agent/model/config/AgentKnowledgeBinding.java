package com.lingxi.workbench.admin.agent.model.config;

import com.lingxi.workbench.admin.agent.common.enums.KnowledgeBindingMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Agent 发布快照中的知识库与向量模型契约。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentKnowledgeBinding {
    private Long knowledgeBaseId;
    private Long embeddingModelInstanceId;
    private KnowledgeBindingMode bindingMode;
    private Integer priority;
}
