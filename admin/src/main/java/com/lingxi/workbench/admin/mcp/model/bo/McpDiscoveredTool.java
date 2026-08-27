package com.lingxi.workbench.admin.mcp.model.bo;

import com.lingxi.workbench.framework.json.JSONObject;
import lombok.Builder;
import lombok.Getter;

/**
 * MCP Server 远端工具描述。
 */
@Getter
@Builder
public class McpDiscoveredTool {

    private final String name;

    private final String title;

    private final String description;

    private final JSONObject inputSchema;
}
