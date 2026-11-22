package com.lingxi.workbench.admin.workflow.model.request;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.admin.workflow.model.config.WorkflowCanvasState;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 保存 Workflow 草稿请求。 */
@Data
public class SaveWorkflowDraftRequest {
    @NotNull(message = "Workflow定义不能为空")
    private WorkflowDefinition definition;
    @NotNull(message = "Workflow画布状态不能为空")
    private WorkflowCanvasState canvasState;
    @NotNull(message = "草稿revision不能为空")
    private Long expectedRevision;
    private String changeNote;
}
