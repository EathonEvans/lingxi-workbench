package com.lingxi.workbench.admin.evaluation.model.vo;

/**
 * 评测资源写入结果。
 *
 * @param id      资源标识
 * @param message 操作结果
 */
public record EvaluationMutationVO(String id, String message) {
}
