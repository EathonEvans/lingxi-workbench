package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

/**
 * @param decision 已通过平台严格校验的执行决策
 * @param tokenUsage 本次Planner模型调用用量
 */
public record PlannerResult(ExecutionDecision decision, TokenUsage tokenUsage) {
}
