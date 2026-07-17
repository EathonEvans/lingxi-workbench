package com.lingxi.workbench.admin.prompt.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.auth.context.SysUserContext;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptPageQuery;
import com.lingxi.workbench.admin.prompt.model.request.SavePromptRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptDetailVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVersionVO;
import com.lingxi.workbench.admin.prompt.service.PromptService;
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
 * 管理端Prompt模板管理接口。
 */
@RestController
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    /**
     * 分页查询Prompt。
     */
    @GetMapping("/list")
    @SaCheckPermission("prompt:list")
    public ApiResponse<PageResult<PromptVO>> list(@Valid PromptPageQuery query) {
        return ApiResponse.success(promptService.page(query));
    }

    /**
     * 查询Prompt编辑详情。
     */
    @GetMapping("/detail/{id}")
    @SaCheckPermission("prompt:list")
    public ApiResponse<PromptDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(promptService.detail(id));
    }

    /**
     * 查询Prompt历史版本。
     */
    @GetMapping("/versions/{id}")
    @SaCheckPermission("prompt:list")
    public ApiResponse<List<PromptVersionVO>> versions(@PathVariable Long id) {
        return ApiResponse.success(promptService.versions(id));
    }

    /**
     * 查询可插入Prompt模板的内置变量。
     */
    @GetMapping("/variables/available")
    @SaCheckPermission("prompt:list")
    public ApiResponse<List<PromptVariableVO>> availableVariables() {
        return ApiResponse.success(promptService.availableVariables());
    }

    /**
     * 创建Prompt及其首个草稿。
     */
    @PostMapping("/create")
    @SaCheckPermission("prompt:manage")
    public ApiResponse<PromptDetailVO> create(@Valid @RequestBody SavePromptRequest request) {
        return ApiResponse.success(promptService.save(null, request, operatorId()));
    }

    /**
     * 修改Prompt元信息和当前草稿；已发布版本不会被覆盖。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission("prompt:manage")
    public ApiResponse<PromptDetailVO> update(
            @PathVariable Long id, @Valid @RequestBody SavePromptRequest request) {
        return ApiResponse.success(promptService.save(id, request, operatorId()));
    }

    /**
     * 发布当前草稿。
     */
    @PostMapping("/publish/{id}")
    @SaCheckPermission("prompt:publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        promptService.publish(id, operatorId());
        return ApiResponse.success();
    }

    /**
     * 将指定历史版本切换为正式生效版本。
     */
    @PostMapping("/activate-version/{id}/{versionId}")
    @SaCheckPermission("prompt:publish")
    public ApiResponse<Void> activateVersion(
            @PathVariable Long id, @PathVariable Long versionId) {
        promptService.activateVersion(id, versionId, operatorId());
        return ApiResponse.success();
    }

    /**
     * 启用或停用Prompt。
     */
    @PostMapping("/change-status/{id}")
    @SaCheckPermission("prompt:manage")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam EnableStatus status) {
        promptService.changeStatus(id, status, operatorId());
        return ApiResponse.success();
    }

    /**
     * 使用示例变量渲染Prompt，只返回预览结果，不调用模型。
     */
    @PostMapping("/preview")
    @SaCheckPermission("prompt:manage")
    public ApiResponse<PreviewPromptVO> preview(@Valid @RequestBody PreviewPromptRequest request) {
        return ApiResponse.success(promptService.preview(request));
    }

    private Long operatorId() {
        return SysUserContext.get().id();
    }
}
