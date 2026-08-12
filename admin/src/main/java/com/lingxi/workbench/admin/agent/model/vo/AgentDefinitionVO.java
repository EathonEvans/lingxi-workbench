package com.lingxi.workbench.admin.agent.model.vo;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import com.lingxi.workbench.admin.agent.model.config.AgentToolPolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.KnowledgeSearchCapabilitySnapshot;
import com.lingxi.workbench.infra.ai.agent.model.PlanExecutorPolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Agent 发布版本的完整定义响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentDefinitionVO {
    private Integer schemaVersion;
    private AgentType agentType;
    private String chatModelInstanceId;
    private String systemPromptVersionId;
    private String userPromptVersionId;
    private List<String> toolCodes;
    private AgentToolPolicy toolPolicy;
    private KnowledgeSearchCapabilitySnapshot knowledgeSearch;
    private GenerationPolicy generationPolicy;
    private PlanExecutorPolicy planExecutorPolicy;
}
