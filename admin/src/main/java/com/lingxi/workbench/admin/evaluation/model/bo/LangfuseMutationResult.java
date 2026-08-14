package com.lingxi.workbench.admin.evaluation.model.bo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;

/**
 * Langfuse 写操作结果。
 *
 * @param status  数据源状态
 * @param message 可展示的结果说明
 * @param id      Langfuse 资源标识
 */
public record LangfuseMutationResult(EvaluationSourceStatus status, String message, String id) {
}
