package com.lingxi.workbench.admin.workflow.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 从历史版本恢复 Workflow 草稿请求。 */
@Data
public class RestoreWorkflowDraftRequest {

    @NotNull(message = "草稿revision不能为空")
    private Long expectedRevision;
}
