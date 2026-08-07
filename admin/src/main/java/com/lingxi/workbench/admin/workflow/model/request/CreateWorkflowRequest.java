package com.lingxi.workbench.admin.workflow.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建 Workflow 请求。 */
@Data
public class CreateWorkflowRequest {
    @NotBlank(message = "Workflow名称不能为空")
    @Size(max = 64, message = "Workflow名称不能超过64个字符")
    private String name;
    @NotBlank(message = "Workflow编码不能为空")
    @Size(max = 64, message = "Workflow编码不能超过64个字符")
    private String code;
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;
}
