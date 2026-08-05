package com.lingxi.workbench.admin.application.model.request;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 保存 Workflow 草稿；Agent版本可为空，表示发布时跟随其当前生效版本。 */
@Data
public class SaveApplicationDraftRequest {
    @NotNull(message = "缺少Workflow定义")
    @Valid
    private WorkflowDefinition workflow;
    /** 可选的 Application 级 Supervisor 协作配置。 */
    @Valid
    private AgentCollaborationDefinition collaboration;
    /** 可选的独立意图树绑定与叶子执行映射。 */
    @Valid
    private ApplicationIntentBinding intentBinding;
    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;
    @Size(max = 255, message = "版本说明不能超过255个字符")
    private String changeNote;
}
