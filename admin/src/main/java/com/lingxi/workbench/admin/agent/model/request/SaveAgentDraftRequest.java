package com.lingxi.workbench.admin.agent.model.request;

import com.lingxi.workbench.admin.agent.model.config.AgentToolPolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import jakarta.validation.Valid;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 保存 Agent 草稿请求。资源引用允许暂时为空，发布前执行完整校验。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SaveAgentDraftRequest {
    private Long chatModelInstanceId;
    private Long systemPromptVersionId;
    private Long userPromptVersionId;
    private Long knowledgeBaseId;
    /** 允许暴露给模型的本地工具编码；空列表表示关闭工具调用。 */
    @NotNull(message = "工具编码列表不能为空")
    @Size(max = 20, message = "单个Agent最多绑定20个工具")
    private List<String> toolCodes;
    /** 领域专家绑定的平台预设 Skill；主Agent必须为空。 */
    @NotNull(message = "Skill列表不能为空")
    @Size(max = 3, message = "单个Agent最多绑定3个自动启用Skill")
    private List<Long> skillIds;
    @NotNull(message = "请配置工具调用策略")
    @Valid
    private AgentToolPolicy toolPolicy;
    @NotNull(message = "请配置向量召回策略")
    @Valid
    private RetrievalPolicy retrievalPolicy;
    @NotNull(message = "请配置回答生成策略")
    @Valid
    private GenerationPolicy generationPolicy;
    @Valid
    private PlanPolicy planPolicy;
    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;
    @Size(max = 255, message = "版本说明不能超过255个字符")
    private String changeNote;
}
