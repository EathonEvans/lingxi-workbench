package com.lingxi.workbench.workflow.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationRuntimePolicyTest {

    @Test
    void shouldReserveConfiguredGraceOnlyAboveSoftTokenBudget() {
        ApplicationRuntimePolicy policy = ApplicationRuntimePolicy.builder()
                .totalTokenBudget(10_001)
                .tokenGracePercent(10)
                .build();

        assertEquals(10, policy.effectiveTokenGracePercent());
        assertEquals(11_002, policy.effectiveTokenHardBudget());
    }

    @Test
    void shouldUseTenPercentGraceWhenPolicyOmitsValue() {
        ApplicationRuntimePolicy policy = ApplicationRuntimePolicy.builder()
                .totalTokenBudget(64_000)
                .build();

        assertEquals(10, policy.effectiveTokenGracePercent());
        assertEquals(70_400, policy.effectiveTokenHardBudget());
    }

}
