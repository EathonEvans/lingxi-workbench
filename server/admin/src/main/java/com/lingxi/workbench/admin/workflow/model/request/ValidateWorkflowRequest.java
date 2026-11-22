package com.lingxi.workbench.admin.workflow.model.request;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 校验当前编辑中的 Workflow 定义，不写入草稿。 */
@Data
public class ValidateWorkflowRequest {

    @NotNull(message = "Workflow定义不能为空")
    private WorkflowDefinition definition;
}
