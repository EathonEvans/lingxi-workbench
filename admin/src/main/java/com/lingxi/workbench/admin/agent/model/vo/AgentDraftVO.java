package com.lingxi.workbench.admin.agent.model.vo;

import com.lingxi.workbench.admin.agent.model.config.AgentMemoryPolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentRuntimePolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentToolPolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.QueryRewritePolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
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
    private String rewriteModelInstanceId;
    private String systemPromptVersionId;
    private String userPromptVersionId;
    private String rewritePromptVersionId;
    private List<String> toolCodes;
    private AgentToolPolicy toolPolicy;
    private AgentKnowledgeBindingVO knowledge;
    private AgentMemoryPolicy memoryPolicy;
    private QueryRewritePolicy rewritePolicy;
    private RetrievalPolicy retrievalPolicy;
    private GenerationPolicy generationPolicy;
    private AgentRuntimePolicy runtimePolicy;
    private Long revision;
    private String changeNote;
}
