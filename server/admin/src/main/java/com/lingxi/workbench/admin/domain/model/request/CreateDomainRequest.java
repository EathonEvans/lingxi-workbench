package com.lingxi.workbench.admin.domain.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增领域请求。 */
@Data
public class CreateDomainRequest {
    private Long parentId;

    @NotBlank(message = "领域名称不能为空")
    @Size(max = 64, message = "领域名称不能超过64个字符")
    private String name;

    @NotBlank(message = "领域编码不能为空")
    @Pattern(regexp = "[a-z][a-z0-9_]*", message = "领域编码必须使用小写字母、数字和下划线")
    @Size(max = 64, message = "领域编码不能超过64个字符")
    private String code;

    @Size(max = 500, message = "领域描述不能超过500个字符")
    private String description;

    private Integer sortOrder;

    @NotNull(message = "领域状态不能为空")
    private EnableStatus status;
}
