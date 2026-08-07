package com.lingxi.workbench.admin.workflow.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发布 Workflow 请求。 */
@Data
public class PublishWorkflowRequest {
    @NotBlank(message = "版本名称不能为空")
    private String versionName;
    private Long expectedDraftRevision;
    private String changeNote;
}
