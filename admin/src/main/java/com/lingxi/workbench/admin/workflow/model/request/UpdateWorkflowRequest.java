package com.lingxi.workbench.admin.workflow.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新 Workflow 基本信息请求。 */
@Data
public class UpdateWorkflowRequest {
    @NotBlank(message = "Workflow名称不能为空")
    @Size(max = 64, message = "Workflow名称不能超过64个字符")
    private String name;
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;
}
