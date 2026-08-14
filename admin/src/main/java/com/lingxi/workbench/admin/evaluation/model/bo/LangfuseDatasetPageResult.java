package com.lingxi.workbench.admin.evaluation.model.bo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import com.lingxi.workbench.framework.convention.PageResult;

/**
 * Langfuse 数据集分页结果。
 *
 * @param status   数据源状态
 * @param message  可展示的状态说明
 * @param datasets 当前页数据集
 */
public record LangfuseDatasetPageResult(EvaluationSourceStatus status, String message,
                                        PageResult<LangfuseDatasetBO> datasets) {
}
