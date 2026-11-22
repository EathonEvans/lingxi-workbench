package com.lingxi.workbench.admin.skill.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.admin.skill.model.request.CreateSkillRequest;
import com.lingxi.workbench.admin.skill.model.request.PublishSkillRequest;
import com.lingxi.workbench.admin.skill.model.request.RevokeSkillVersionRequest;
import com.lingxi.workbench.admin.skill.model.request.SaveSkillDraftRequest;
import com.lingxi.workbench.admin.skill.model.request.SkillPageQuery;
import com.lingxi.workbench.admin.skill.model.request.UpdateSkillRequest;
import com.lingxi.workbench.admin.skill.model.vo.SkillDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillValidationResultVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionVO;
import com.lingxi.workbench.admin.skill.service.SkillService;
import com.lingxi.workbench.admin.tool.model.vo.ToolOptionVO;
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

/** 管理端 Skill 草稿、发布版本和撤销治理接口。 */
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping("/list")
    @SaCheckPermission("skill:list")
    public ApiResponse<PageResult<SkillVO>> list(@Valid SkillPageQuery query) {
        return ApiResponse.success(skillService.page(query, UserContext.userId()));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("skill:list")
    public ApiResponse<SkillDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(skillService.detail(id, UserContext.userId()));
    }

    @GetMapping("/versions/{id}")
    @SaCheckPermission("skill:list")
    public ApiResponse<List<SkillVersionVO>> versions(@PathVariable Long id) {
        return ApiResponse.success(skillService.versions(id, UserContext.userId()));
    }

    @GetMapping("/version-detail/{id}/{versionId}")
    @SaCheckPermission("skill:list")
    public ApiResponse<SkillVersionDetailVO> versionDetail(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(skillService.versionDetail(id, versionId, UserContext.userId()));
    }

    @GetMapping("/tool-options")
    @SaCheckPermission("skill:list")
    public ApiResponse<List<ToolOptionVO>> toolOptions() {
        return ApiResponse.success(skillService.toolOptions());
    }

    @PostMapping("/create")
    @SaCheckPermission("skill:manage")
    public ApiResponse<SkillDetailVO> create(@Valid @RequestBody CreateSkillRequest request) {
        return ApiResponse.success(skillService.create(request, UserContext.userId()));
    }

    @PostMapping("/update/{id}")
    @SaCheckPermission("skill:manage")
    public ApiResponse<SkillDetailVO> update(
            @PathVariable Long id, @Valid @RequestBody UpdateSkillRequest request) {
        return ApiResponse.success(skillService.update(id, request, UserContext.userId()));
    }

    @PostMapping("/save-draft/{id}")
    @SaCheckPermission("skill:manage")
    public ApiResponse<SkillDetailVO> saveDraft(
            @PathVariable Long id, @Valid @RequestBody SaveSkillDraftRequest request) {
        return ApiResponse.success(skillService.saveDraft(id, request, UserContext.userId()));
    }

    @PostMapping("/validate/{id}")
    @SaCheckPermission("skill:manage")
    public ApiResponse<SkillValidationResultVO> validate(@PathVariable Long id) {
        return ApiResponse.success(skillService.validate(id, UserContext.userId()));
    }

    @PostMapping("/publish/{id}")
    @SaCheckPermission("skill:publish")
    public ApiResponse<SkillVersionDetailVO> publish(
            @PathVariable Long id, @Valid @RequestBody PublishSkillRequest request) {
        return ApiResponse.success(skillService.publish(id, request, UserContext.userId()));
    }

    @PostMapping("/activate-version/{id}/{versionId}")
    @SaCheckPermission("skill:publish")
    public ApiResponse<Void> activateVersion(@PathVariable Long id, @PathVariable Long versionId) {
        skillService.activateVersion(id, versionId, UserContext.userId());
        return ApiResponse.success();
    }

    @PostMapping("/revoke-version/{id}/{versionId}")
    @SaCheckPermission("skill:revoke")
    public ApiResponse<Void> revokeVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @Valid @RequestBody RevokeSkillVersionRequest request) {
        skillService.revokeVersion(id, versionId, request, UserContext.userId());
        return ApiResponse.success();
    }

    @PostMapping("/change-status/{id}")
    @SaCheckPermission("skill:manage")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam SkillLifecycleStatus status) {
        skillService.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }
}
