package com.lingxi.workbench.admin.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeBasePageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeBaseRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeBaseService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeAccessRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessOptionVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessVO;
import java.util.List;

/**
 * 管理端知识库接口。
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;

    /**
     * 分页查询知识库。
     */
    @GetMapping("/list")
    @SaCheckPermission("knowledge:list")
    public ApiResponse<PageResult<KnowledgeBaseVO>> list(@Valid KnowledgeBasePageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    /**
     * 查询知识库详情。
     */
    @GetMapping("/detail/{id}")
    @SaCheckPermission("knowledge:list")
    public ApiResponse<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    /**
     * 新增知识库。
     */
    @PostMapping("/create")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody SaveKnowledgeBaseRequest request) {
        return ApiResponse.success(service.save(null, request, UserContext.userId()));
    }

    /**
     * 修改知识库。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<KnowledgeBaseVO> update(@PathVariable Long id, @Valid @RequestBody SaveKnowledgeBaseRequest request) {
        return ApiResponse.success(service.save(id, request, UserContext.userId()));
    }

    /**
     * 删除无文档的知识库。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, UserContext.userId());
        return ApiResponse.success();
    }

    /** 读取范围仅对有知识库管理权限的管理员开放。 */
    @GetMapping("/access/{id}")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<KnowledgeAccessVO> access(@PathVariable Long id) {
        return ApiResponse.success(service.access(id));
    }

    /** 实时调整 RAG 读取范围，不修改 Agent 或索引发布快照。 */
    @PostMapping("/access/update/{id}")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<KnowledgeAccessVO> saveAccess(@PathVariable Long id, @Valid @RequestBody SaveKnowledgeAccessRequest request) {
        return ApiResponse.success(service.saveAccess(id, request, UserContext.userId()));
    }
    /** 知识库管理人员只读取授权所需的账号标识和名称。 */
    @GetMapping("/access/options/users")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<PageResult<KnowledgeAccessOptionVO>> userOptions(@Valid SysUserPageQuery query) {
        return ApiResponse.success(service.userOptions(query));
    }

    /** 授权角色选择器。 */
    @GetMapping("/access/options/roles")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<List<KnowledgeAccessOptionVO>> roleOptions() {
        return ApiResponse.success(service.roleOptions());
    }

    /** 授权直属部门选择器。 */
    @GetMapping("/access/options/departments")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<List<KnowledgeAccessOptionVO>> departmentOptions() {
        return ApiResponse.success(service.departmentOptions());
    }

}
