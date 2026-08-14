package com.lingxi.workbench.admin.application.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建 Application 稳定身份请求。 */
@Data
public class CreateApplicationRequest {
    @NotBlank(message = "请输入应用名称")
    @Size(max = 64, message = "应用名称不能超过64个字符")
    private String name;

    @NotBlank(message = "请输入应用编码")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "应用编码仅支持小写字母、数字、中划线和下划线")
    private String code;

    @Size(max = 500, message = "应用描述不能超过500个字符")
    private String description;

    @NotNull(message = "请选择一级领域")
    private Long domainId;
}
