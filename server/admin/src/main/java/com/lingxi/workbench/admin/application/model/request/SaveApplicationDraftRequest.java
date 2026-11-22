package com.lingxi.workbench.admin.application.model.request;

import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 保存 Application 草稿；资源版本可为空，表示发布时锁定其当前生效版本。 */
@Data
public class SaveApplicationDraftRequest {
    /** 可选的 Application 级 Supervisor 协作配置。 */
    @Valid
    private AgentCollaborationDefinition collaboration;
    /** 意图路由，只配置允许确定性直达的叶子。 */
    @Valid
    private ApplicationIntentBinding intentRouting;
    /** Application 级长期记忆配置。 */
    @Valid
    private MemoryConfig memory;
    /** 路由前问题重写与共享上下文分区预算。 */
    @Valid
    private ApplicationContextPolicy contextPolicy;
    /** Application 显式绑定的 Workflow 能力；Agent 能力由领域范围自动生成。 */
    @Valid
    private CapabilityCatalogSnapshot capabilityCatalog;
    /** 单轮 Application 执行总预算。 */
    @Valid
    private ApplicationRuntimePolicy runtimePolicy;
    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;
    @Size(max = 255, message = "版本说明不能超过255个字符")
    private String changeNote;
}
