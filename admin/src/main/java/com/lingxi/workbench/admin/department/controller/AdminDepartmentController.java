package com.lingxi.workbench.admin.department.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.lingxi.workbench.admin.department.model.request.CreateDepartmentRequest;
import com.lingxi.workbench.admin.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.department.model.vo.AdminDepartmentVO;
import com.lingxi.workbench.admin.department.service.AdminDepartmentService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端部门管理 API。
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;

    /** 查询部门树，供组织架构页面和部门选择器使用。 */
    @GetMapping("/tree")
    @SaCheckPermission("system:department:list")
    public ApiResponse<List<AdminDepartmentVO>> listTree() {
        return ApiResponse.success(adminDepartmentService.listTree());
    }

    /** 新增部门。 */
    @PostMapping("/create")
    @SaCheckPermission("system:department:create")
    public ApiResponse<AdminDepartmentVO> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.success(adminDepartmentService.create(request));
    }

    /** 修改部门基础信息或调整上级部门。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:department:update")
    public ApiResponse<AdminDepartmentVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.success(adminDepartmentService.update(id, request));
    }

    /** 删除没有子节点且未被任何账号或角色引用的部门。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("system:department:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminDepartmentService.delete(id);
        return ApiResponse.success();
    }
}
