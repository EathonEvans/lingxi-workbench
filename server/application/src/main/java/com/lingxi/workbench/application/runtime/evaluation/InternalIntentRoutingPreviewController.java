package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.chat.ops.InternalOpsTokenVerifier;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供 Admin 后端调用的 Application 草稿意图测试接口。 */
@RestController
@RequestMapping("/internal/v1/intent-routing")
@RequiredArgsConstructor
public class InternalIntentRoutingPreviewController {

    private static final String OPS_TOKEN_HEADER = "X-Lingxi-Ops-Token";

    private final InternalOpsTokenVerifier tokenVerifier;
    private final IntentRoutingPreviewService previewService;
    private final IntentRoutingEvaluationService evaluationService;

    @PostMapping("/preview")
    public ApiResponse<IntentRoutingPreviewResult> preview(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody IntentRoutingPreviewRequest request) {
        tokenVerifier.verify(token);
        return ApiResponse.success(previewService.preview(request));
    }

    @PostMapping("/preview-version")
    public ApiResponse<IntentRoutingPreviewResult> previewVersion(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody IntentRoutingVersionPreviewRequest request) {
        tokenVerifier.verify(token);
        return ApiResponse.success(previewService.previewVersion(request));
    }

    /** 导出与正式版本绑定的评测模板，Admin 只负责填充人工标注样本。 */
    @PostMapping("/evaluation-template")
    public ApiResponse<IntentRoutingEvaluationDataset> evaluationTemplate(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody IntentRoutingEvaluationTemplateRequest request) {
        tokenVerifier.verify(token);
        return ApiResponse.success(evaluationService.exportDatasetTemplate(
                request.applicationId(), request.applicationVersionId(), request.datasetVersion()));
    }

    /** 在执行任何模型调用前校验人工数据集及版本锁定契约。 */
    @PostMapping("/evaluation-validate")
    public ApiResponse<Boolean> validateEvaluation(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody IntentRoutingEvaluationDataset dataset) {
        tokenVerifier.verify(token);
        evaluationService.validate(dataset);
        return ApiResponse.success(true);
    }

    /** 对 Admin 已逐条执行的结果统一生成生产门禁报告。 */
    @PostMapping("/evaluation-report")
    public ApiResponse<IntentRoutingEvaluationReport> evaluationReport(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody IntentRoutingEvaluationReportRequest request) {
        tokenVerifier.verify(token);
        return ApiResponse.success(evaluationService.report(request.dataset(), request.results()));
    }
}
