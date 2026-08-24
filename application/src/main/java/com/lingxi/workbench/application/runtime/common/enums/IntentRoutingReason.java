package com.lingxi.workbench.application.runtime.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 意图路由最终动作的稳定原因码，用于审计、观测和安全降级证据。 */
@Getter
@RequiredArgsConstructor
public enum IntentRoutingReason {

    NO_CANDIDATE("NO_CANDIDATE", "候选召回为空"),
    UNMAPPED_CANDIDATE("UNMAPPED_CANDIDATE", "候选缺少发布映射"),
    ROUTING_DISABLED("ROUTING_DISABLED", "Application未启用意图路由"),
    ROUTED_BY_SEMANTIC("ROUTED_BY_SEMANTIC", "语义决策选择锁定候选"),
    SEMANTIC_CLARIFICATION("SEMANTIC_CLARIFICATION", "语义决策要求澄清"),
    SEMANTIC_DEFER("SEMANTIC_DEFER", "语义决策交回fallback Agent"),
    MODEL_BUDGET_EXHAUSTED("MODEL_BUDGET_EXHAUSTED", "Application模型调用或Token工作预算已用尽"),
    SEMANTIC_DECISION_FAILED("SEMANTIC_DECISION_FAILED", "语义决策失败"),
    CLARIFICATION_RESOLVED("CLARIFICATION_RESOLVED", "澄清已完成"),
    CLARIFICATION_RETRY("CLARIFICATION_RETRY", "澄清需要重试"),
    CLARIFICATION_CANCELLED("CLARIFICATION_CANCELLED", "用户取消澄清"),
    CLARIFICATION_EXPIRED("CLARIFICATION_EXPIRED", "澄清会话过期"),
    CLARIFICATION_FAILED("CLARIFICATION_FAILED", "澄清失败");

    private final String code;
    private final String describe;
}
