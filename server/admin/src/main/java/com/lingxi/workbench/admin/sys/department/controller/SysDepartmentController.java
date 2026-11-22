package com.lingxi.workbench.admin.sys.department.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.sys.department.model.request.CreateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.vo.SysDepartmentVO;
import com.lingxi.workbench.admin.sys.department.service.SysDepartmentService;
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
 *
 * <p>提供部门树的查询、新增、修改及删除接口，各接口均受 Sa-Token 权限控制。</p>
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class SysDepartmentController {

    private final SysDepartmentService sysDepartmentService;

    /**
     * 查询完整部门树。
     *
     * <p>供组织架构页面和部门选择器使用。</p>
     *
     * @return 部门树根节点列表
     */
    @GetMapping("/tree")
    @SaCheckPermission("system:department:list")
    public ApiResponse<List<SysDepartmentVO>> listTree() {
        return ApiResponse.success(sysDepartmentService.listTree());
    }

    /**
     * 新增部门。
     *
     * @param request 新增部门请求
     * @return 新增后的部门视图
     */
    @PostMapping("/create")
    @SaCheckPermission("system:department:create")
    public ApiResponse<SysDepartmentVO> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.success(sysDepartmentService.create(request));
    }

    /**
     * 修改部门基础信息或调整上级部门。
     *
     * @param id      部门主键
     * @param request 修改请求
     * @return 修改后的部门视图
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:department:update")
    public ApiResponse<SysDepartmentVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.success(sysDepartmentService.update(id, request));
    }

    /**
     * 删除部门。
     *
     * <p>仅当无子部门、无管理员归属且无角色引用时允许删除。</p>
     *
     * @param id 部门主键
     * @return 空响应体
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("system:department:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysDepartmentService.delete(id);
        return ApiResponse.success();
    }
}
