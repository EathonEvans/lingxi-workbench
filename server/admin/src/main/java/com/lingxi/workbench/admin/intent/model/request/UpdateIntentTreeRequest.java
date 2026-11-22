package com.lingxi.workbench.admin.intent.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改意图树名称和描述请求。 */
@Data
public class UpdateIntentTreeRequest {

    @NotBlank(message = "请输入意图树名称")
    @Size(max = 64, message = "意图树名称不能超过64个字符")
    private String name;

    @Size(max = 500, message = "意图树描述不能超过500个字符")
    private String description;

    @NotNull(message = "请选择所属领域")
    private Long domainId;
}
