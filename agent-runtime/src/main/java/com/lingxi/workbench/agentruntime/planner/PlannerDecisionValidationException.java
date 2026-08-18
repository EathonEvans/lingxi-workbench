package com.lingxi.workbench.agentruntime.planner;

/** 模型已返回结构化对象，但不满足发布能力和计划业务规则。 */
public class PlannerDecisionValidationException extends RuntimeException {

    public PlannerDecisionValidationException(String message) {
        super(message);
    }
}
