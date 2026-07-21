package com.lingxi.workbench.admin.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.auth.context.SysUserContext;
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

/** 管理端知识库接口。 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;

    /** 分页查询知识库。 */
    @GetMapping("/list")
    @SaCheckPermission("knowledge:list")
    public ApiResponse<PageResult<KnowledgeBaseVO>> list(@Valid KnowledgeBasePageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    /** 查询知识库详情。 */
    @GetMapping("/detail/{id}")
    @SaCheckPermission("knowledge:list")
    public ApiResponse<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    /** 新增知识库。 */
    @PostMapping("/create")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody SaveKnowledgeBaseRequest request) {
        return ApiResponse.success(service.save(null, request, operatorId()));
    }

    /** 修改知识库。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<KnowledgeBaseVO> update(@PathVariable Long id, @Valid @RequestBody SaveKnowledgeBaseRequest request) {
        return ApiResponse.success(service.save(id, request, operatorId()));
    }

    /** 删除无文档的知识库。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("knowledge:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, operatorId());
        return ApiResponse.success();
    }

    private Long operatorId() {
        return SysUserContext.get().id();
    }
}
