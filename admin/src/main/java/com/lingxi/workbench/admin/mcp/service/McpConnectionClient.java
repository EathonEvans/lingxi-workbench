package com.lingxi.workbench.admin.mcp.service;

import com.lingxi.workbench.admin.mcp.model.bo.McpConnectionConfig;
import com.lingxi.workbench.admin.mcp.model.bo.McpInspectionResult;

/**
 * MCP 外部协议边界，负责初始化远端 Server 并发现工具。
 */
public interface McpConnectionClient {

    McpInspectionResult inspect(McpConnectionConfig config);
}
