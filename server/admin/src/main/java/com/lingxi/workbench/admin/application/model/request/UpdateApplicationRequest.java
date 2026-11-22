package com.lingxi.workbench.admin.application.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改 Application 名称和描述请求。 */
@Data
public class UpdateApplicationRequest {
    @NotBlank(message = "请输入应用名称")
    @Size(max = 64, message = "应用名称不能超过64个字符")
    private String name;
    @Size(max = 500, message = "应用描述不能超过500个字符")
    private String description;
    @Size(max = 255, message = "应用头像标识不能超过255个字符")
    private String avatar;
    @NotNull(message = "请选择一级领域")
    private Long domainId;
}
