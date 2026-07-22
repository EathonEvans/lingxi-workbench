package com.lingxi.workbench.admin.model.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.model.request.CreateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.request.ModelCredentialPageQuery;
import com.lingxi.workbench.admin.model.model.request.RotateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelCredentialVO;
import com.lingxi.workbench.admin.model.service.ModelCredentialService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端模型配置接口。
 */
@RestController
@RequestMapping("/api/v1/model-config/credentials")
@RequiredArgsConstructor
public class ModelCredentialController {

    private final ModelCredentialService service;


    /**
     * 分页查询模型访问密钥，响应中不包含密文。
     */
    @GetMapping("/list")
    @SaCheckPermission(value = {"model:credential:list", "model:instance:list"}, mode = SaMode.OR)
    public ApiResponse<PageResult<ModelCredentialVO>> credentials(@Valid ModelCredentialPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    /**
     * 查询 API Key 选项，供模型实例表单使用，响应中不包含密文。
     */
    @GetMapping("/options")
    @SaCheckPermission("model:instance:list")
    public ApiResponse<List<ModelCredentialVO>> credentialOptions() {
        return ApiResponse.success(service.options());
    }

    /**
     * 新增模型访问密钥。
     */
    @PostMapping("/create")
    @SaCheckPermission("model:credential:manage")
    public ApiResponse<ModelCredentialVO> createCredential(@Valid @RequestBody CreateModelCredentialRequest request) {
        return ApiResponse.success(service.create(request, UserContext.userId()));
    }

    /**
     * 使用新值覆盖原密钥，旧值不会保留。
     */
    @PostMapping("/rotate/{id}")
    @SaCheckPermission("model:credential:manage")
    public ApiResponse<Void> rotateCredential(@PathVariable Long id, @Valid @RequestBody RotateModelCredentialRequest request) {
        service.rotate(id, request, UserContext.userId());
        return ApiResponse.success();
    }

    /**
     * 启停或永久撤销模型访问密钥。
     */
    @PostMapping("/change-status/{id}")
    @SaCheckPermission("model:credential:manage")
    public ApiResponse<Void> changeCredentialStatus(@PathVariable Long id, @RequestParam ModelCredentialStatus status) {
        service.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }
}
