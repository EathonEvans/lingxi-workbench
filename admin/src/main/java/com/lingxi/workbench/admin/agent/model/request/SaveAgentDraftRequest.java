package com.lingxi.workbench.admin.agent.model.request;

import com.lingxi.workbench.admin.agent.model.config.AgentMemoryPolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentRuntimePolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.QueryRewritePolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 保存 Agent 草稿请求。资源引用允许暂时为空，发布前执行完整校验。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SaveAgentDraftRequest {
    private Long chatModelInstanceId;
    private Long rewriteModelInstanceId;
    private Long systemPromptVersionId;
    private Long userPromptVersionId;
    private Long rewritePromptVersionId;
    private Long knowledgeBaseId;
    @NotNull(message = "请配置会话记忆策略")
    @Valid
    private AgentMemoryPolicy memoryPolicy;
    @NotNull(message = "请配置问题改写策略")
    @Valid
    private QueryRewritePolicy rewritePolicy;
    @NotNull(message = "请配置向量召回策略")
    @Valid
    private RetrievalPolicy retrievalPolicy;
    @NotNull(message = "请配置回答生成策略")
    @Valid
    private GenerationPolicy generationPolicy;
    @NotNull(message = "请配置Agent运行限制")
    @Valid
    private AgentRuntimePolicy runtimePolicy;
    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;
    @Size(max = 255, message = "版本说明不能超过255个字符")
    private String changeNote;
}
