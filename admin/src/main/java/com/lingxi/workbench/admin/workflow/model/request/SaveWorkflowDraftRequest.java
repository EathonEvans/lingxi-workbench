package com.lingxi.workbench.admin.workflow.model.request;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 保存 Workflow 草稿请求。 */
@Data
public class SaveWorkflowDraftRequest {
    @NotNull(message = "Workflow定义不能为空")
    private WorkflowDefinition definition;
    private Long expectedRevision;
    private String changeNote;
}
