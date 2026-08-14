package com.lingxi.workbench.admin.evaluation.model.bo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;

import java.util.List;

/**
 * Langfuse Score 游标分页结果。
 *
 * @param status     数据源状态
 * @param message    可展示的状态说明
 * @param scores     当前页评分
 * @param nextCursor 下一页游标
 */
public record LangfuseScorePageResult(EvaluationSourceStatus status, String message,
                                      List<LangfuseScoreBO> scores, String nextCursor) {
}
