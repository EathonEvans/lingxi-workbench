package com.lingxi.workbench.agentruntime.model;

/** 外部写能力可能已成功，但本地终态确认失败，运行必须进入待对账而不能自动重放。 */
public class AgentReconciliationPendingException extends RuntimeException {

    public AgentReconciliationPendingException(RuntimeException cause) {
        super("外部能力效果等待本地对账", cause);
    }
}
