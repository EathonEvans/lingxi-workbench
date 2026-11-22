package com.lingxi.workbench.workflow.model;

/** 能力调用风险等级，由平台策略决定是否需要额外确认。 */
public enum CapabilityRiskLevel {
    READ_ONLY,
    WRITE,
    HIGH_RISK
}
