package com.lingxi.workbench.admin.tool.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import com.lingxi.workbench.admin.tool.model.config.ToolParameterDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

import static com.lingxi.workbench.admin.tool.common.constants.ToolConstants.MAX_PARAMETER_COUNT;

/** 新建 Tool 请求。 */
@Data
public class CreateToolRequest {

    @NotBlank(message = "请输入 Tool 名称")
    @Size(max = 64, message = "Tool 名称不能超过 64 个字符")
    private String name;

    @NotBlank(message = "请输入 Tool 编码")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "Tool 编码仅支持小写字母、数字和下划线")
    private String code;

    @NotBlank(message = "请输入 Tool 描述")
    @Size(max = 1000, message = "Tool 描述不能超过 1000 个字符")
    private String description;

    @NotNull(message = "请选择 Tool 来源")
    private ToolSourceType sourceType;

    @Valid
    @NotNull(message = "参数列表不能为空")
    @Size(max = MAX_PARAMETER_COUNT, message = "一个 Tool 最多配置 5 个参数")
    private List<ToolParameterDefinition> parameters;

    @NotNull(message = "请选择 Tool 状态")
    private EnableStatus status;
}
