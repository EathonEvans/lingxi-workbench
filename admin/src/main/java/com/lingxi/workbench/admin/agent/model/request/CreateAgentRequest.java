package com.lingxi.workbench.admin.agent.model.request;

import com.lingxi.workbench.admin.agent.common.enums.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建 Agent 稳定身份请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateAgentRequest {
    @NotBlank(message = "请输入Agent名称")
    @Size(max = 64, message = "Agent名称不能超过64个字符")
    private String name;
    @NotBlank(message = "请输入Agent编码")
    @Pattern(
            regexp = "^[a-z][a-z0-9_-]{1,63}$",
            message = "Agent编码仅支持小写字母、数字、中划线和下划线")
    private String code;
    @Size(max = 500, message = "Agent描述不能超过500个字符")
    private String description;
    @NotNull(message = "请选择Agent类型")
    private AgentType agentType;
}
