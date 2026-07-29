package com.lingxi.workbench.admin.agent.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 修改 Agent 元信息请求；编码和类型创建后不可变。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UpdateAgentRequest {
    @NotBlank(message = "请输入Agent名称")
    @Size(max = 64, message = "Agent名称不能超过64个字符")
    private String name;
    @Size(max = 500, message = "Agent描述不能超过500个字符")
    private String description;
}
