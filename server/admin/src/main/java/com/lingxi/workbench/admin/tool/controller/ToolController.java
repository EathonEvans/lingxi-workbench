package com.lingxi.workbench.admin.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.model.request.CreateToolRequest;
import com.lingxi.workbench.admin.tool.model.request.ToolPageQuery;
import com.lingxi.workbench.admin.tool.model.request.UpdateToolRequest;
import com.lingxi.workbench.admin.tool.model.vo.ToolOptionVO;
import com.lingxi.workbench.admin.tool.model.vo.ToolVO;
import com.lingxi.workbench.admin.tool.service.ToolService;
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

import java.util.List;

/**
 * Tool 定义管理接口。
 */
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    /**
     * 分页查询 Tool 定义。
     */
    @GetMapping("/list")
    @SaCheckPermission("tool:list")
    public ApiResponse<PageResult<ToolVO>> list(@Valid ToolPageQuery query) {
        return ApiResponse.success(toolService.page(query));
    }

    /**
     * 查询 Tool 完整定义。
     */
    @GetMapping("/detail/{id}")
    @SaCheckPermission("tool:list")
    public ApiResponse<ToolVO> detail(@PathVariable Long id) {
        return ApiResponse.success(toolService.detail(id));
    }

    /**
     * 查询 Agent 可绑定的启用 Tool。
     */
    @GetMapping("/options")
    @SaCheckPermission("agent:list")
    public ApiResponse<List<ToolOptionVO>> options() {
        return ApiResponse.success(toolService.activeOptions());
    }

    /**
     * 创建 Tool 稳定定义。
     */
    @PostMapping("/create")
    @SaCheckPermission("tool:manage")
    public ApiResponse<ToolVO> create(@Valid @RequestBody CreateToolRequest request) {
        return ApiResponse.success(toolService.create(request, UserContext.userId()));
    }

    /**
     * 修改 Tool 展示信息和参数契约。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission("tool:manage")
    public ApiResponse<ToolVO> update(@PathVariable Long id, @Valid @RequestBody UpdateToolRequest request) {
        return ApiResponse.success(toolService.update(id, request, UserContext.userId()));
    }

    /**
     * 启用或停用 Tool。
     */
    @PostMapping("/change-status/{id}")
    @SaCheckPermission("tool:manage")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam EnableStatus status) {
        toolService.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }
}
