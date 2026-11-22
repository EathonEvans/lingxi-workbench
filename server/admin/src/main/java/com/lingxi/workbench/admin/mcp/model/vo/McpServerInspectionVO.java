package com.lingxi.workbench.admin.mcp.model.vo;

import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MCP Server 连通性与工具发现结果。
 */
@Data
@Builder
public class McpServerInspectionVO {

    private McpConnectionTestStatus status;

    private Integer latencyMs;

    private String errorCode;

    private String message;

    private String serverName;

    private String serverVersion;

    private String protocolVersion;

    private List<McpDiscoveredToolVO> tools;
}
