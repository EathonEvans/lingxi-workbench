package com.lingxi.workbench.admin.agent.model.vo;

import com.lingxi.workbench.admin.agent.model.config.AgentToolPolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Agent 当前可编辑草稿响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentDraftVO {
    private String id;
    private String baseVersionId;
    private Integer schemaVersion;
    private String chatModelInstanceId;
    private String systemPromptVersionId;
    private String userPromptVersionId;
    private List<String> toolCodes;
    private List<String> skillIds;
    private AgentToolPolicy toolPolicy;
    private AgentKnowledgeBindingVO knowledge;
    private RetrievalPolicy retrievalPolicy;
    private GenerationPolicy generationPolicy;
    private PlanPolicy planPolicy;
    private Long revision;
    private String changeNote;
}
