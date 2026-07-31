package com.lingxi.workbench.admin.mcp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.mcp.model.request.CreateMcpServerRequest;
import com.lingxi.workbench.admin.mcp.model.request.ImportMcpToolRequest;
import com.lingxi.workbench.admin.mcp.model.request.McpServerPageQuery;
import com.lingxi.workbench.admin.mcp.model.request.UpdateMcpServerRequest;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerInspectionVO;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerVO;
import com.lingxi.workbench.admin.mcp.service.McpServerService;
import com.lingxi.workbench.admin.mcp.service.McpToolImportService;
import com.lingxi.workbench.admin.tool.model.vo.ToolVO;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Server 管理、连接测试与工具发现接口。
 */
@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpToolImportService mcpToolImportService;

    @GetMapping("/list")
    @SaCheckPermission("mcp-server:list")
    public ApiResponse<PageResult<McpServerVO>> list(@Valid McpServerPageQuery query) {
        return ApiResponse.success(mcpServerService.page(query));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("mcp-server:list")
    public ApiResponse<McpServerVO> detail(@PathVariable Long id) {
        return ApiResponse.success(mcpServerService.detail(id));
    }

    @PostMapping("/create")
    @SaCheckPermission("mcp-server:manage")
    public ApiResponse<McpServerVO> create(@Valid @RequestBody CreateMcpServerRequest request) {
        return ApiResponse.success(mcpServerService.create(request, UserContext.userId()));
    }

    @PostMapping("/update/{id}")
    @SaCheckPermission("mcp-server:manage")
    public ApiResponse<McpServerVO> update(@PathVariable Long id, @Valid @RequestBody UpdateMcpServerRequest request) {
        return ApiResponse.success(mcpServerService.update(id, request, UserContext.userId()));
    }

    @PostMapping("/change-status/{id}")
    @SaCheckPermission("mcp-server:manage")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam EnableStatus status) {
        mcpServerService.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }

    @PostMapping("/test-connection/{id}")
    @SaCheckPermission("mcp-server:manage")
    public ApiResponse<McpServerInspectionVO> testConnection(@PathVariable Long id) {
        return ApiResponse.success(mcpServerService.testConnection(id, UserContext.userId()));
    }

    @PostMapping("/discover-tools/{id}")
    @SaCheckPermission("mcp-server:manage")
    public ApiResponse<McpServerInspectionVO> discoverTools(@PathVariable Long id) {
        return ApiResponse.success(mcpServerService.discoverTools(id, UserContext.userId()));
    }

    @PostMapping("/import-tool/{id}")
    @SaCheckPermission({"mcp-server:manage", "tool:manage"})
    public ApiResponse<ToolVO> importTool(@PathVariable Long id, @Valid @RequestBody ImportMcpToolRequest request) {
        return ApiResponse.success(mcpToolImportService.importDiscoveredTool(id, request, UserContext.userId()));
    }
}
