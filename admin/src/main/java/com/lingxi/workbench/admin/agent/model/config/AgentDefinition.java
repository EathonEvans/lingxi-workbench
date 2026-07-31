package com.lingxi.workbench.admin.agent.model.config;

import com.lingxi.workbench.admin.agent.common.enums.AgentType;
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
    private AgentType agentType;
    private Long chatModelInstanceId;
    private Long rewriteModelInstanceId;
    /** Schema V2 回答阶段 SYSTEM PromptVersion。 */
    private Long systemPromptVersionId;
    /** Schema V2 回答阶段 USER PromptVersion。 */
    private Long userPromptVersionId;
    /**
     * Schema V1 遗留的回答 PromptVersion，语义等同于 SYSTEM Prompt。
     *
     * <p>只用于读取已经发布的不可变历史版本，新版本不再写入该字段。</p>
     */
    private Long answerPromptVersionId;
    private Long rewritePromptVersionId;
    /** 当前版本允许暴露给模型的本地工具编码。 */
    private List<String> toolCodes;
    /** 工具调用运行边界。 */
    private AgentToolPolicy toolPolicy;
    private AgentKnowledgeBinding knowledge;
    private AgentMemoryPolicy memoryPolicy;
    private QueryRewritePolicy rewritePolicy;
    private RetrievalPolicy retrievalPolicy;
    private GenerationPolicy generationPolicy;
    private AgentRuntimePolicy runtimePolicy;

    /** 兼容读取 Schema V1 历史快照中的回答 System Prompt。 */
    public Long resolveSystemPromptVersionId() {
        if (systemPromptVersionId != null) {
            return systemPromptVersionId;
        }
        return answerPromptVersionId;
    }
}
