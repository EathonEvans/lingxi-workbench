package com.lingxi.workbench.chat.model.bo;

/**
 * Plan-and-Execute单个步骤的安全文本增量。
 *
 * @param runId 本次Planner运行标识
 * @param planNo 计划版本号
 * @param stepId 步骤标识
 * @param content 可向用户展示的文本增量
 */
public record ChatStepOutputEventBO(String runId, Integer planNo, String stepId, String content) {
}
