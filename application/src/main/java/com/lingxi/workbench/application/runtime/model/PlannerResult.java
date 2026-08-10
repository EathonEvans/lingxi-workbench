package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

/**
 * @param decision   已通过平台严格校验或由平台安全构造的执行决策
 * @param tokenUsage 本次Planner模型调用用量
 * @param modelCalls 本次决策实际模型调用次数
 */
public record PlannerResult(ExecutionDecision decision, TokenUsage tokenUsage, int modelCalls) {
}
