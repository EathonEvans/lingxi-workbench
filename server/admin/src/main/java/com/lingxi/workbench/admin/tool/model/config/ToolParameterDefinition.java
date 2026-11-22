package com.lingxi.workbench.admin.tool.model.config;

import com.lingxi.workbench.admin.tool.common.enums.ToolParameterDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运营人员维护的单个 Tool 参数。
 *
 * <p>该结构是管理接口的输入输出契约，JSON Schema 由后端统一构造，
 * 前端不直接提交协议字符串。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolParameterDefinition {

    @NotBlank(message = "请输入参数字段名")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$", message = "参数字段名必须以字母开头，仅支持字母、数字和下划线")
    private String name;

    @NotBlank(message = "请输入参数描述")
    @Size(max = 200, message = "参数描述不能超过 200 个字符")
    private String description;

    @NotNull(message = "请选择参数类型")
    private ToolParameterDataType dataType;

    @NotNull(message = "请选择参数是否必填")
    private Boolean required;
}
