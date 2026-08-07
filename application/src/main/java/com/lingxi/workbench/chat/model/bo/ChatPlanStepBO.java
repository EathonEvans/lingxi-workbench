package com.lingxi.workbench.chat.model.bo;

/**
 * @param stepId         计划内稳定步骤标识
 * @param name           用户可见步骤名称
 * @param capabilityCode 发布能力编码
 */
public record ChatPlanStepBO(String stepId, String name, String capabilityCode) {
}
