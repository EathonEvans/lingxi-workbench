package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;

/**
 * @param stepId         计划内稳定步骤标识
 * @param name           用户可见步骤名称
 * @param capabilityCode 发布能力编码
 * @param status         AgentScope任务快照状态
 */
public record ChatPlanStepBO(String stepId, String name, String capabilityCode, ExecutionStepStatus status) {
}
