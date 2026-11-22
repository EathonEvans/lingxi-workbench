package com.lingxi.workbench.admin.mcp.model.request;

import com.lingxi.workbench.admin.mcp.common.enums.McpAuthenticationType;
import com.lingxi.workbench.admin.mcp.common.enums.McpTransportType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.MAX_TIMEOUT_SECONDS;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.MIN_TIMEOUT_SECONDS;

/**
 * 新建 MCP Server 请求。
 */
@Data
public class CreateMcpServerRequest {

    @NotBlank(message = "请输入 MCP Server 名称")
    @Size(max = 64, message = "MCP Server 名称不能超过 64 个字符")
    private String name;

    @NotBlank(message = "请输入 MCP Server 编码")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "MCP Server 编码仅支持小写字母、数字和下划线")
    private String code;

    @Size(max = 500, message = "MCP Server 描述不能超过 500 个字符")
    private String description;

    @NotNull(message = "请选择 MCP 传输协议")
    private McpTransportType transportType;

    @NotBlank(message = "请输入 MCP Server 地址")
    @Size(max = 500, message = "MCP Server 地址不能超过 500 个字符")
    private String baseUrl;

    @NotBlank(message = "请输入 MCP Endpoint")
    @Size(max = 255, message = "MCP Endpoint 不能超过 255 个字符")
    private String endpoint;

    @NotNull(message = "请选择 MCP 认证方式")
    private McpAuthenticationType authenticationType;

    @Size(max = 4096, message = "Bearer Token 不能超过 4096 个字符")
    private String bearerToken;

    @NotNull(message = "请输入 MCP 请求超时")
    @Min(value = MIN_TIMEOUT_SECONDS, message = "MCP 请求超时不能小于 1 秒")
    @Max(value = MAX_TIMEOUT_SECONDS, message = "MCP 请求超时不能超过 60 秒")
    private Integer requestTimeoutSeconds;
}
