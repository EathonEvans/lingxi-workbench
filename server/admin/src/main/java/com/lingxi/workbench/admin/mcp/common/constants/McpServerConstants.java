package com.lingxi.workbench.admin.mcp.common.constants;

/**
 * MCP Server 控制面与客户端边界常量。
 */
public interface McpServerConstants {

    String DEFAULT_ENDPOINT = "/mcp";

    String CLIENT_NAME = "lingxi-workbench-admin";

    String CLIENT_VERSION = "1.0.0";

    String INVALID_ADDRESS_ERROR_CODE = "INVALID_ADDRESS";

    String TIMEOUT_ERROR_CODE = "TIMEOUT";

    String CONNECTION_ERROR_CODE = "CONNECTION_ERROR";

    int DEFAULT_TIMEOUT_SECONDS = 20;

    int MIN_TIMEOUT_SECONDS = 1;

    int MAX_TIMEOUT_SECONDS = 60;

    int MAX_DISCOVERED_TOOL_COUNT = 200;
}
