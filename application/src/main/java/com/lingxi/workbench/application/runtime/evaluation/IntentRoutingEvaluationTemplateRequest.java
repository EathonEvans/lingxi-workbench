package com.lingxi.workbench.application.runtime.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 导出锁定版本评测模板的请求。
 *
 * @param applicationId        Application 主键
 * @param applicationVersionId ApplicationVersion 主键
 * @param datasetVersion       外部数据集快照版本
 */
public record IntentRoutingEvaluationTemplateRequest(
        @NotNull Long applicationId,
        @NotNull Long applicationVersionId,
        @NotBlank @Size(max = 100) String datasetVersion) {
}
