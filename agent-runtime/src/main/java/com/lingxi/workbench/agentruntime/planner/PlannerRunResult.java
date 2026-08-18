package com.lingxi.workbench.agentruntime.planner;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

/**
 * @param decision      已通过 Runtime 校验或由平台安全构造的决策
 * @param tokenUsage    本次 Planner 所有模型尝试的实际用量
 * @param modelCalls    本次 Planner 实际模型调用次数
 * @param lastSequenceNo 最后一个 Runtime 事件序号
 */
public record PlannerRunResult(PlannerDecision decision, TokenUsage tokenUsage, int modelCalls,
                               long lastSequenceNo) {
}
