package com.lingxi.workbench.admin.domain.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改领域请求，稳定编码创建后不可修改。 */
@Data
public class UpdateDomainRequest {
    private Long parentId;

    @NotBlank(message = "领域名称不能为空")
    @Size(max = 64, message = "领域名称不能超过64个字符")
    private String name;

    @Size(max = 500, message = "领域描述不能超过500个字符")
    private String description;

    private Integer sortOrder;

    @NotNull(message = "领域状态不能为空")
    private EnableStatus status;
}
