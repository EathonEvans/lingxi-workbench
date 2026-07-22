package com.lingxi.workbench.admin.model.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.model.request.ModelProviderPageQuery;
import com.lingxi.workbench.admin.model.model.request.SaveModelProviderRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelProviderVO;
import com.lingxi.workbench.admin.model.service.ModelProviderService;
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

/** 管理端模型配置接口。 */
@RestController
@RequestMapping("/api/v1/model-config/providers")
@RequiredArgsConstructor
public class ModelProviderController {

    private final ModelProviderService service;

    /** 分页查询模型供应商。 */
    @GetMapping("/list")
    @SaCheckPermission(
            value = {"model:provider:list", "model:credential:list", "model:instance:list"},
            mode = SaMode.OR)
    public ApiResponse<PageResult<ModelProviderVO>> providers(@Valid ModelProviderPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    /** 查询供应商选项，供 API Key 和模型实例表单使用。 */
    @GetMapping("/options")
    @SaCheckPermission(
            value = {"model:provider:list", "model:credential:list", "model:instance:list"},
            mode = SaMode.OR)
    public ApiResponse<List<ModelProviderVO>> providerOptions() {
        return ApiResponse.success(service.options());
    }

    /** 新增模型供应商。 */
    @PostMapping("/create")
    @SaCheckPermission("model:provider:manage")
    public ApiResponse<ModelProviderVO> createProvider(
            @Valid @RequestBody SaveModelProviderRequest request) {
        return ApiResponse.success(service.save(null, request, UserContext.userId()));
    }

    /** 修改模型供应商。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("model:provider:manage")
    public ApiResponse<ModelProviderVO> updateProvider(
            @PathVariable Long id, @Valid @RequestBody SaveModelProviderRequest request) {
        return ApiResponse.success(service.save(id, request, UserContext.userId()));
    }

    /** 启用或停用模型供应商。 */
    @PostMapping("/change-status/{id}")
    @SaCheckPermission("model:provider:manage")
    public ApiResponse<Void> changeProviderStatus(
            @PathVariable Long id, @RequestParam EnableStatus status) {
        service.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }
}
