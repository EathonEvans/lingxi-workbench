package com.lingxi.workbench.admin.tool.model.request;

import com.lingxi.workbench.admin.tool.model.config.ToolParameterDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

import static com.lingxi.workbench.admin.tool.common.constants.ToolConstants.MAX_PARAMETER_COUNT;

/**
 * 修改 Tool 请求。
 *
 * <p>稳定编码和来源不允许在更新接口中提交，避免已绑定的 Agent 与运行时执行器失联。</p>
 */
@Data
public class UpdateToolRequest {

    @NotBlank(message = "请输入 Tool 名称")
    @Size(max = 64, message = "Tool 名称不能超过 64 个字符")
    private String name;

    @NotBlank(message = "请输入 Tool 描述")
    @Size(max = 1000, message = "Tool 描述不能超过 1000 个字符")
    private String description;

    @Valid
    @NotNull(message = "参数列表不能为空")
    @Size(max = MAX_PARAMETER_COUNT, message = "一个 Tool 最多配置 5 个参数")
    private List<ToolParameterDefinition> parameters;
}
