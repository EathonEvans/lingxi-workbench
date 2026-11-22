package com.lingxi.workbench.admin.evaluation.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param applicationId        Application主键
 * @param applicationVersionId ApplicationVersion主键
 * @param datasetName          Langfuse回归数据集名称
 */
public record EvaluationReadinessRequest(
        @NotNull(message = "请选择Application") Long applicationId,
        @NotNull(message = "请选择ApplicationVersion") Long applicationVersionId,
        @NotBlank(message = "请选择评测数据集")
        @Size(max = 100, message = "数据集名称不能超过100字") String datasetName) {
}
