package com.lingxi.workbench.admin.mcp.model.bo;

import lombok.Builder;
import lombok.Getter;

/**
 * 发起一次 MCP 连接所需的最小配置。
 */
@Getter
@Builder
public class McpConnectionConfig {

    private final String baseUrl;

    private final String endpoint;

    private final String bearerToken;

    private final int requestTimeoutSeconds;
}
