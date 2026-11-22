package com.lingxi.workbench.admin.intent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.intent.model.request.CreateIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.request.IntentTreePageQuery;
import com.lingxi.workbench.admin.intent.model.request.PublishIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.request.SaveIntentTreeDraftRequest;
import com.lingxi.workbench.admin.intent.model.request.UpdateIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeValidationResultVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionVO;
import com.lingxi.workbench.admin.intent.service.IntentTreeService;
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

/** 意图树稳定身份、草稿和发布版本管理接口。 */
@RestController
@RequestMapping("/api/v1/intent-trees")
@RequiredArgsConstructor
public class IntentTreeController {

    private final IntentTreeService intentTreeService;

    @GetMapping("/list")
    @SaCheckPermission("intent-tree:list")
    public ApiResponse<PageResult<IntentTreeVO>> list(@Valid IntentTreePageQuery query) {
        return ApiResponse.success(intentTreeService.page(query));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("intent-tree:list")
    public ApiResponse<IntentTreeDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(intentTreeService.detail(id));
    }

    @GetMapping("/versions/{id}")
    @SaCheckPermission("intent-tree:list")
    public ApiResponse<List<IntentTreeVersionVO>> versions(@PathVariable Long id) {
        return ApiResponse.success(intentTreeService.versions(id));
    }

    @GetMapping("/version-detail/{id}/{versionId}")
    @SaCheckPermission("intent-tree:list")
    public ApiResponse<IntentTreeVersionDetailVO> versionDetail(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(intentTreeService.versionDetail(id, versionId));
    }

    @PostMapping("/create")
    @SaCheckPermission("intent-tree:manage")
    public ApiResponse<IntentTreeDetailVO> create(@Valid @RequestBody CreateIntentTreeRequest request) {
        return ApiResponse.success(intentTreeService.create(request, UserContext.userId()));
    }

    @PostMapping("/update/{id}")
    @SaCheckPermission("intent-tree:manage")
    public ApiResponse<IntentTreeDetailVO> update(@PathVariable Long id, @Valid @RequestBody UpdateIntentTreeRequest request) {
        return ApiResponse.success(intentTreeService.update(id, request, UserContext.userId()));
    }

    @PostMapping("/save-draft/{id}")
    @SaCheckPermission("intent-tree:manage")
    public ApiResponse<IntentTreeDetailVO> saveDraft(@PathVariable Long id, @Valid @RequestBody SaveIntentTreeDraftRequest request) {
        return ApiResponse.success(intentTreeService.saveDraft(id, request, UserContext.userId()));
    }

    @PostMapping("/validate/{id}")
    @SaCheckPermission("intent-tree:manage")
    public ApiResponse<IntentTreeValidationResultVO> validate(@PathVariable Long id) {
        return ApiResponse.success(intentTreeService.validate(id));
    }

    @PostMapping("/publish/{id}")
    @SaCheckPermission("intent-tree:publish")
    public ApiResponse<IntentTreeVersionDetailVO> publish(@PathVariable Long id, @Valid @RequestBody PublishIntentTreeRequest request) {
        return ApiResponse.success(intentTreeService.publish(id, request, UserContext.userId()));
    }

    @PostMapping("/activate-version/{id}/{versionId}")
    @SaCheckPermission("intent-tree:publish")
    public ApiResponse<Void> activateVersion(@PathVariable Long id, @PathVariable Long versionId) {
        intentTreeService.activateVersion(id, versionId, UserContext.userId());
        return ApiResponse.success();
    }

    @PostMapping("/change-status/{id}")
    @SaCheckPermission("intent-tree:manage")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam EnableStatus status) {
        intentTreeService.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }
}
