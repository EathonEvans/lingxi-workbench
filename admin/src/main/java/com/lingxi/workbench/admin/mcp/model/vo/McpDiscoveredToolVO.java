package com.lingxi.workbench.admin.mcp.model.vo;

import com.alibaba.fastjson2.JSONObject;
import lombok.Builder;
import lombok.Data;

/**
 * MCP 工具发现结果。
 */
@Data
@Builder
public class McpDiscoveredToolVO {

    private String name;

    private String title;

    private String description;

    private JSONObject inputSchema;
}
