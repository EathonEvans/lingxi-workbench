package com.lingxi.workbench.admin.mcp.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 将发现的远端 MCP 工具导入为平台 Tool 的请求。
 */
@Data
public class ImportMcpToolRequest {

    @NotBlank(message = "远端工具名称不能为空")
    @Size(max = 128, message = "远端工具名称不能超过 128 个字符")
    private String remoteToolName;

    @NotBlank(message = "请输入平台 Tool 编码")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "Tool 编码仅支持小写字母、数字和下划线")
    private String code;

    @NotBlank(message = "请输入平台 Tool 名称")
    @Size(max = 64, message = "Tool 名称不能超过 64 个字符")
    private String name;

    @NotBlank(message = "请输入 Tool 调用说明")
    @Size(max = 1000, message = "Tool 调用说明不能超过 1000 个字符")
    private String description;
}
