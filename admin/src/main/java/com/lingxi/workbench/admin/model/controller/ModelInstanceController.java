package com.lingxi.workbench.admin.model.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.auth.context.SysUserContext;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.model.request.SaveModelInstanceRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelInstanceVO;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理端模型配置接口。 */
@RestController
@RequestMapping("/api/v1/model-config/instances")
@RequiredArgsConstructor
public class ModelInstanceController {

    private final ModelInstanceService service;


    /** 分页查询模型实例。 */
    @GetMapping("/list")
    @SaCheckPermission("model:instance:list")
    public ApiResponse<PageResult<ModelInstanceVO>> instances(@Valid PageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    /** 新增模型实例。 */
    @PostMapping("/create")
    @SaCheckPermission("model:instance:manage")
    public ApiResponse<ModelInstanceVO> createInstance(
            @Valid @RequestBody SaveModelInstanceRequest request) {
        return ApiResponse.success(service.save(null, request, operatorId()));
    }

    /** 修改模型实例。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("model:instance:manage")
    public ApiResponse<ModelInstanceVO> updateInstance(
            @PathVariable Long id, @Valid @RequestBody SaveModelInstanceRequest request) {
        return ApiResponse.success(service.save(id, request, operatorId()));
    }

    /** 启用或停用模型实例。 */
    @PostMapping("/change-status/{id}")
    @SaCheckPermission("model:instance:manage")
    public ApiResponse<Void> changeInstanceStatus(
            @PathVariable Long id, @RequestParam EnableStatus status) {
        service.changeStatus(id, status, operatorId());
        return ApiResponse.success();
    }

    /** 将指定实例设置为当前模型类型的默认实例。 */
    @PostMapping("/set-default/{id}")
    @SaCheckPermission("model:instance:default")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        service.setDefault(id, operatorId());
        return ApiResponse.success();
    }

    /** 使用实例当前配置调用供应商接口并记录连通性测试结果。 */
    @PostMapping("/test-connection/{id}")
    @SaCheckPermission("model:instance:test")
    public ApiResponse<ModelInstanceVO> testConnection(@PathVariable Long id) {
        return ApiResponse.success(service.testConnection(id, operatorId()));
    }

    private Long operatorId() {
        return SysUserContext.get().id();
    }
}
