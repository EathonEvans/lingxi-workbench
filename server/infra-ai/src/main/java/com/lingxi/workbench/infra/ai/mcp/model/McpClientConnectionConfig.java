package com.lingxi.workbench.infra.ai.mcp.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 建立一次动态 MCP 客户端连接所需的最小配置。
 */
@Getter
@Builder
public class McpClientConnectionConfig {

    private final String baseUrl;

    private final String endpoint;

    private final String bearerToken;

    private final int requestTimeoutSeconds;
}
