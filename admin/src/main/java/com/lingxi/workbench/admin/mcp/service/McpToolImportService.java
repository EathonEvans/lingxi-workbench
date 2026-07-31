package com.lingxi.workbench.admin.mcp.service;

import com.lingxi.workbench.admin.mcp.model.bo.McpDiscoveredTool;
import com.lingxi.workbench.admin.mcp.model.request.ImportMcpToolRequest;
import com.lingxi.workbench.admin.tool.model.bo.McpToolImport;
import com.lingxi.workbench.admin.tool.model.vo.ToolVO;
import com.lingxi.workbench.admin.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * MCP 工具发现与 Tool 持久化之间的用例编排。
 *
 * <p>远程发现不进入数据库事务；发现成功后再调用 ToolService 完成原子持久化。</p>
 */
@Service
@RequiredArgsConstructor
public class McpToolImportService {

    private final McpServerService mcpServerService;
    private final ToolService toolService;

    public ToolVO importDiscoveredTool(Long mcpServerId, ImportMcpToolRequest request, Long operatorId) {
        McpDiscoveredTool discoveredTool = mcpServerService.discoverTool(mcpServerId, request.getRemoteToolName(), operatorId);
        McpToolImport source = McpToolImport.builder()
                .mcpServerId(mcpServerId)
                .remoteToolName(discoveredTool.getName())
                .code(request.getCode().trim().toLowerCase())
                .name(request.getName().trim())
                .description(request.getDescription().trim())
                .parameterSchema(discoveredTool.getInputSchema())
                .build();
        return toolService.importMcpTool(source, operatorId);
    }
}
