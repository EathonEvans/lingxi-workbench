package com.lingxi.workbench.admin.application.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.application.model.request.ApplicationPageQuery;
import com.lingxi.workbench.admin.application.model.request.CreateApplicationRequest;
import com.lingxi.workbench.admin.application.model.request.PublishApplicationRequest;
import com.lingxi.workbench.admin.application.model.request.SaveApplicationDraftRequest;
import com.lingxi.workbench.admin.application.model.request.UpdateApplicationRequest;
import com.lingxi.workbench.admin.application.model.vo.ApplicationDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationResultVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionVO;
import com.lingxi.workbench.admin.application.service.ApplicationService;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
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
 * Application 元信息、Workflow草稿和发布版本管理接口。
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService applicationService;

    /** 分页查询 Application 列表，支持关键字和状态过滤。 */
    @GetMapping("/list")
    @SaCheckPermission("application:list")
    public ApiResponse<PageResult<ApplicationVO>> list(@Valid ApplicationPageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /** 查看 Application 编辑详情，包含草稿 Workflow 和发布版本摘要。 */
    @GetMapping("/detail/{id}")
    @SaCheckPermission("application:list")
    public ApiResponse<ApplicationDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(applicationService.detail(id));
    }

    /** 查看 Application 所有历史发布版本列表。 */
    @GetMapping("/versions/{id}")
    @SaCheckPermission("application:list")
    public ApiResponse<List<ApplicationVersionVO>> versions(@PathVariable Long id) {
        return ApiResponse.success(applicationService.versions(id));
    }

    /** 查看指定发布版本的完整 Workflow 快照详情。 */
    @GetMapping("/version-detail/{id}/{versionId}")
    @SaCheckPermission("application:list")
    public ApiResponse<ApplicationVersionDetailVO> versionDetail(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(applicationService.versionDetail(id, versionId));
    }

    /** 创建 Application 稳定身份及默认 Workflow 草稿（Start → End）。 */
    @PostMapping("/create")
    @SaCheckPermission("application:manage")
    public ApiResponse<ApplicationDetailVO> create(@Valid @RequestBody CreateApplicationRequest request) {
        return ApiResponse.success(applicationService.create(request, UserContext.userId()));
    }

    /** 修改 Application 名称和描述元信息。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("application:manage")
    public ApiResponse<ApplicationDetailVO> update(@PathVariable Long id, @Valid @RequestBody UpdateApplicationRequest request) {
        return ApiResponse.success(applicationService.update(id, request, UserContext.userId()));
    }

    /** 保存 Application 草稿，使用 revision 实现并发编辑保护。 */
    @PostMapping("/save-draft/{id}")
    @SaCheckPermission("application:manage")
    public ApiResponse<ApplicationDetailVO> saveDraft(@PathVariable Long id, @Valid @RequestBody SaveApplicationDraftRequest request) {
        return ApiResponse.success(applicationService.saveDraft(id, request, UserContext.userId()));
    }

    /** 对当前草稿执行发布前校验：Workflow 结构、Agent 节点引用解析。 */
    @PostMapping("/validate/{id}")
    @SaCheckPermission("application:manage")
    public ApiResponse<ApplicationValidationResultVO> validate(@PathVariable Long id) {
        return ApiResponse.success(applicationService.validate(id));
    }

    /** 校验并发布 Application 当前草稿，锁定 Agent 版本后生成不可变快照。 */
    @PostMapping("/publish/{id}")
    @SaCheckPermission("application:publish")
    public ApiResponse<ApplicationVersionDetailVO> publish(@PathVariable Long id, @Valid @RequestBody PublishApplicationRequest request) {
        return ApiResponse.success(applicationService.publish(id, request, UserContext.userId()));
    }

    /** 切换 Application 当前生效的发布版本。 */
    @PostMapping("/activate-version/{id}/{versionId}")
    @SaCheckPermission("application:publish")
    public ApiResponse<Void> activateVersion(@PathVariable Long id, @PathVariable Long versionId) {
        applicationService.activateVersion(id, versionId, UserContext.userId());
        return ApiResponse.success();
    }

    /** 启用或停用 Application。 */
    @PostMapping("/change-status/{id}")
    @SaCheckPermission("application:manage")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam EnableStatus status) {
        applicationService.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }
}
