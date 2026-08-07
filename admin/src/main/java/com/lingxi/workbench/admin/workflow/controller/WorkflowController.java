package com.lingxi.workbench.admin.workflow.controller;

import com.lingxi.workbench.admin.workflow.model.request.CreateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.PublishWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.SaveWorkflowDraftRequest;
import com.lingxi.workbench.admin.workflow.model.request.UpdateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.WorkflowPageQuery;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowDetailVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVO;
import com.lingxi.workbench.admin.workflow.service.WorkflowService;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Workflow 管理接口。 */
@RestController
@RequestMapping("/api/admin/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /** 分页查询 Workflow 列表。 */
    @GetMapping
    public ApiResponse<PageResult<WorkflowVO>> page(@Valid WorkflowPageQuery query) {
        return ApiResponse.success(workflowService.page(query));
    }

    /** 查询 Workflow 详情。 */
    @GetMapping("/{id}")
    public ApiResponse<WorkflowDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(workflowService.detail(id));
    }

    /** 创建 Workflow。 */
    @PostMapping
    public ApiResponse<WorkflowDetailVO> create(@Valid @RequestBody CreateWorkflowRequest request) {
        return ApiResponse.success(workflowService.create(request, UserContext.userId()));
    }

    /** 更新 Workflow 基本信息。 */
    @PutMapping("/{id}")
    public ApiResponse<WorkflowDetailVO> update(@PathVariable Long id, @Valid @RequestBody UpdateWorkflowRequest request) {
        return ApiResponse.success(workflowService.update(id, request, UserContext.userId()));
    }

    /** 保存 Workflow 草稿。 */
    @PutMapping("/{id}/draft")
    public ApiResponse<WorkflowDetailVO> saveDraft(@PathVariable Long id, @Valid @RequestBody SaveWorkflowDraftRequest request) {
        return ApiResponse.success(workflowService.saveDraft(id, request, UserContext.userId()));
    }

    /** 发布 Workflow 版本。 */
    @PostMapping("/{id}/publish")
    public ApiResponse<WorkflowDetailVO> publish(@PathVariable Long id, @Valid @RequestBody PublishWorkflowRequest request) {
        return ApiResponse.success(workflowService.publish(id, request, UserContext.userId()));
    }
}
