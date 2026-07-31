package com.lingxi.workbench.admin.mcp.model.request;

import com.lingxi.workbench.admin.mcp.common.enums.McpAuthenticationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.MAX_TIMEOUT_SECONDS;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.MIN_TIMEOUT_SECONDS;

/**
 * 修改 MCP Server 请求。
 *
 * <p>稳定编码和传输协议创建后不允许修改；Bearer Token 留空时保留原密钥。</p>
 */
@Data
public class UpdateMcpServerRequest {

    @NotBlank(message = "请输入 MCP Server 名称")
    @Size(max = 64, message = "MCP Server 名称不能超过 64 个字符")
    private String name;

    @Size(max = 500, message = "MCP Server 描述不能超过 500 个字符")
    private String description;

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
