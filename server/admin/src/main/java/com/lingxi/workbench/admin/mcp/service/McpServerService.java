package com.lingxi.workbench.admin.mcp.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.mcp.model.request.CreateMcpServerRequest;
import com.lingxi.workbench.admin.mcp.model.request.McpServerPageQuery;
import com.lingxi.workbench.admin.mcp.model.request.UpdateMcpServerRequest;
import com.lingxi.workbench.admin.mcp.model.bo.McpDiscoveredTool;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerInspectionVO;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerVO;
import com.lingxi.workbench.framework.convention.PageResult;

/**
 * MCP Server 控制面业务能力。
 */
public interface McpServerService {

    PageResult<McpServerVO> page(McpServerPageQuery query);

    McpServerVO detail(Long id);

    McpServerVO create(CreateMcpServerRequest request, Long operatorId);

    McpServerVO update(Long id, UpdateMcpServerRequest request, Long operatorId);

    void changeStatus(Long id, EnableStatus status, Long operatorId);

    McpServerInspectionVO testConnection(Long id, Long operatorId);

    McpServerInspectionVO discoverTools(Long id, Long operatorId);

    McpDiscoveredTool discoverTool(Long id, String remoteToolName, Long operatorId);

    void validateRuntimeAvailable(Long id);
}
