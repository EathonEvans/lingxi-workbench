package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationExecutionBudgetTest {

    @Test
    void shouldRejectMissingRuntimePolicy() {
        assertThrows(NullPointerException.class, () -> ApplicationExecutionBudget.fromRuntimePolicy(null));
    }

    @Test
    void shouldShareDelegationLimitThroughOneLedger() {
        ApplicationExecutionBudget budget = ApplicationExecutionBudget.fromRuntimePolicy(
                ApplicationRuntimePolicy.builder().maxDelegations(1).build());

        budget.reserveDelegation();

        assertEquals(0, budget.remainingDelegations());
        assertThrows(BusinessException.class, budget::reserveDelegation);
    }

    @Test
    void shouldApplySoftTokenBudgetAndFinalizationGrace() {
        ApplicationExecutionBudget budget = ApplicationExecutionBudget.fromRuntimePolicy(
                ApplicationRuntimePolicy.builder().totalTokenBudget(20).build());

        budget.recordUsage(TokenUsage.of(8, 7, 15));
        assertEquals(5, budget.remainingTokenBudget());
        budget.recordUsage(TokenUsage.of(3, 3, 6));
        assertEquals(1, budget.remainingFinalizationTokenBudget());
        budget.recordUsage(TokenUsage.of(1, 1, 2));

        assertEquals(23, budget.tokenUsage().totalTokens());
        assertTrue(budget.shouldFinalize());
        assertFalse(budget.hasFinalizationCapacity());
        assertThrows(BusinessException.class, budget::remainingFinalizationTokenBudget);
    }

    @Test
    void shouldShareModelAndToolCallBudgetsThroughOneLedger() {
        ApplicationExecutionBudget budget = ApplicationExecutionBudget.fromRuntimePolicy(
                ApplicationRuntimePolicy.builder().maxModelCalls(2).maxToolCalls(3)
                        .totalTokenBudget(100).build());

        budget.reserveModelCall();
        budget.reserveFinalModelCall();
        budget.reserveToolCalls(2);
        budget.reserveToolCalls(1);

        assertEquals(0, budget.remainingModelCalls());
        assertEquals(0, budget.remainingToolCalls());
        assertThrows(BusinessException.class, budget::reserveModelCall);
        assertThrows(BusinessException.class, () -> budget.reserveToolCalls(1));
    }
}
