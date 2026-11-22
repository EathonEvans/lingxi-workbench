package com.lingxi.workbench.agentruntime.plan;

/** Runtime 用于副作用去重的能力风险级别。 */
public enum RuntimeCapabilityRisk {
    READ_ONLY,
    WRITE,
    HIGH_RISK;

    public boolean hasSideEffect() {
        return this != READ_ONLY;
    }
}
