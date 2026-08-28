package com.lingxi.workbench.admin.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationCaseRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationDatasetRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationScoreRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationRunRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationDatasetQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationOverviewQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationScoreQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationRunQuery;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationDatasetPageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationCaseListVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMutationVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationOverviewVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationScorePageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationRunDetailVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationRunVO;
import com.lingxi.workbench.admin.evaluation.service.EvaluationService;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测中心接口，前端不直接访问外部观测服务。
 */
@Validated
@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/overview")
    @SaCheckPermission("evaluation:list")
    public ApiResponse<EvaluationOverviewVO> overview(@Valid EvaluationOverviewQuery query) {
        return ApiResponse.success(evaluationService.overview(query));
    }

    @GetMapping("/scores")
    @SaCheckPermission("evaluation:list")
    public ApiResponse<EvaluationScorePageVO> scores(@Valid EvaluationScoreQuery query) {
        return ApiResponse.success(evaluationService.scores(query));
    }

    @GetMapping("/datasets")
    @SaCheckPermission("evaluation:list")
    public ApiResponse<EvaluationDatasetPageVO> datasets(@Valid EvaluationDatasetQuery query) {
        return ApiResponse.success(evaluationService.datasets(query));
    }

    @PostMapping("/scores")
    @SaCheckPermission("evaluation:manage")
    public ApiResponse<EvaluationMutationVO> createScore(@Valid @RequestBody CreateEvaluationScoreRequest request) {
        return ApiResponse.success(evaluationService.createScore(request, UserContext.userId()));
    }

    @PostMapping("/datasets")
    @SaCheckPermission("evaluation:manage")
    public ApiResponse<EvaluationMutationVO> createDataset(@Valid @RequestBody CreateEvaluationDatasetRequest request) {
        return ApiResponse.success(evaluationService.createDataset(request, UserContext.userId()));
    }

    @PostMapping("/datasets/{datasetName}/cases")
    @SaCheckPermission("evaluation:manage")
    public ApiResponse<EvaluationMutationVO> createCase(
            @PathVariable @Size(max = 100, message = "数据集名称不能超过100字") String datasetName,
            @Valid @RequestBody CreateEvaluationCaseRequest request) {
        return ApiResponse.success(evaluationService.createCase(datasetName, request, UserContext.userId()));
    }

    @GetMapping("/datasets/{datasetName}/cases")
    @SaCheckPermission("evaluation:list")
    public ApiResponse<EvaluationCaseListVO> cases(
            @PathVariable @Size(max = 100, message = "数据集名称不能超过100字") String datasetName) {
        return ApiResponse.success(evaluationService.cases(datasetName));
    }

    @GetMapping("/runs")
    @SaCheckPermission("evaluation:list")
    public ApiResponse<PageResult<EvaluationRunVO>> runs(@Valid EvaluationRunQuery query) {
        return ApiResponse.success(evaluationService.runs(query));
    }

    @GetMapping("/runs/{runId}")
    @SaCheckPermission("evaluation:list")
    public ApiResponse<EvaluationRunDetailVO> runDetail(@PathVariable Long runId) {
        return ApiResponse.success(evaluationService.runDetail(runId));
    }

    @PostMapping("/runs")
    @SaCheckPermission("evaluation:execute")
    public ApiResponse<EvaluationRunVO> createRun(
            @Valid @RequestBody CreateEvaluationRunRequest request) {
        return ApiResponse.success(evaluationService.createRun(request, UserContext.userId()));
    }

    @PostMapping("/runs/{runId}/cancel")
    @SaCheckPermission("evaluation:execute")
    public ApiResponse<Void> cancelRun(@PathVariable Long runId) {
        evaluationService.cancelRun(runId);
        return ApiResponse.success();
    }

    @PostMapping("/runs/{runId}/retry")
    @SaCheckPermission("evaluation:execute")
    public ApiResponse<Void> retryRun(@PathVariable Long runId) {
        evaluationService.retryRun(runId);
        return ApiResponse.success();
    }
}
