package com.lingxi.workbench.admin.agent.model.config;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 不可变 Agent Version 中保存的完整行为定义。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentDefinition {
    private Integer schemaVersion;
    private AgentRole role;
    private Long chatModelInstanceId;
    /** Schema V2 回答阶段 SYSTEM PromptVersion。 */
    private Long systemPromptVersionId;
    /** Schema V2 回答阶段 USER PromptVersion。 */
    private Long userPromptVersionId;
    /** 当前版本允许暴露给模型的本地工具编码。 */
    private List<String> toolCodes;
    /** 工具调用运行边界。 */
    private AgentToolPolicy toolPolicy;
    /** Schema V4 ReAct知识搜索能力；资源与索引版本均在发布时冻结。 */
    private KnowledgeSearchCapabilitySnapshot knowledgeSearch;
    private GenerationPolicy generationPolicy;
    /** 仅主Agent使用的结构性规划边界。 */
    private PlanPolicy planPolicy;
}
