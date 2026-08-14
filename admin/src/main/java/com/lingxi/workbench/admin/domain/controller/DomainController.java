package com.lingxi.workbench.admin.domain.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.domain.model.request.CreateDomainRequest;
import com.lingxi.workbench.admin.domain.model.request.UpdateDomainRequest;
import com.lingxi.workbench.admin.domain.model.vo.DomainVO;
import com.lingxi.workbench.admin.domain.service.DomainService;
import com.lingxi.workbench.framework.context.UserContext;
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

/** 管理端领域目录接口。 */
@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
public class DomainController {
    private final DomainService service;

    @GetMapping("/tree")
    @SaCheckPermission("domain:list")
    public ApiResponse<List<DomainVO>> tree() {
        return ApiResponse.success(service.listTree());
    }

    @PostMapping("/create")
    @SaCheckPermission("domain:create")
    public ApiResponse<DomainVO> create(@Valid @RequestBody CreateDomainRequest request) {
        return ApiResponse.success(service.create(request, UserContext.userId()));
    }

    @PostMapping("/update/{id}")
    @SaCheckPermission("domain:update")
    public ApiResponse<DomainVO> update(@PathVariable Long id, @Valid @RequestBody UpdateDomainRequest request) {
        return ApiResponse.success(service.update(id, request, UserContext.userId()));
    }

    @PostMapping("/delete/{id}")
    @SaCheckPermission("domain:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }
}
