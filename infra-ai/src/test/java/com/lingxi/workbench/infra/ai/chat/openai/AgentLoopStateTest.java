package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.model.AgentExecutionBudget;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopStateTest {

    @Test
    void shouldRequestFinalAnswerWhenTotalTokenSoftLimitIsReached() {
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 5, 100, 100, 80, 88,
                AgentExecutionBudget.noop()));

        state.beginModelCall();
        state.recordUsage(TokenUsage.of(70, 10, 80));

        assertTrue(state.shouldFinalize());
    }

    @Test
    void shouldReserveFinalModelCallAfterWorkTokenLimit() {
        AtomicInteger finalCalls = new AtomicInteger();
        AgentExecutionBudget budget = new AgentExecutionBudget() {
            @Override
            public void reserveModelCall() {
            }

            @Override
            public void reserveFinalModelCall() {
                finalCalls.incrementAndGet();
            }

            @Override
            public void reserveToolCalls(int calls) {
            }
        };
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 5, 0, 0, 80, 88, budget));
        state.recordUsage(TokenUsage.of(70, 10, 80));

        state.beginFinalModelCall();

        assertEquals(1, finalCalls.get());
    }

    @Test
    void shouldRejectModelCallAfterInputTokenLimit() {
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 5, 100, 100));
        state.recordUsage(TokenUsage.of(100, 5, 105));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.lingxi.workbench.infra.ai.tool.AgentLoopLimitException.class, state::beginModelCall);
    }

    @Test
    void shouldRejectToolBatchBeyondRemainingBudget() {
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 2, 100, 100));
        assertTrue(state.tryRecordToolCalls(1));

        assertFalse(state.tryRecordToolCalls(2));
    }

    @Test
    void shouldReserveSharedBudgetForEveryModelAndToolCall() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AgentExecutionBudget budget = new AgentExecutionBudget() {
            @Override
            public void reserveModelCall() {
                modelCalls.incrementAndGet();
            }

            @Override
            public void reserveToolCalls(int calls) {
                toolCalls.addAndGet(calls);
            }
        };
        AgentLoopState state = new AgentLoopState(runtimeConfig(3, 3, 100, 100, budget));

        state.beginModelCall();
        state.beginModelCall();
        assertTrue(state.tryRecordToolCalls(2));

        assertEquals(2, modelCalls.get());
        assertEquals(2, toolCalls.get());
    }

    private static AgentToolRuntimeConfig runtimeConfig(int maxModelCalls, int maxToolCalls, int maxInputTokens, int maxOutputTokens) {
        return runtimeConfig(maxModelCalls, maxToolCalls, maxInputTokens, maxOutputTokens,
                AgentExecutionBudget.noop());
    }

    private static AgentToolRuntimeConfig runtimeConfig(int maxModelCalls, int maxToolCalls, int maxInputTokens,
                                                        int maxOutputTokens, AgentExecutionBudget executionBudget) {
        return runtimeConfig(maxModelCalls, maxToolCalls, maxInputTokens, maxOutputTokens, 0, 0, executionBudget);
    }

    private static AgentToolRuntimeConfig runtimeConfig(int maxModelCalls, int maxToolCalls, int maxInputTokens,
                                                        int maxOutputTokens, int maxTotalTokens,
                                                        int maxFinalizationTokens,
                                                        AgentExecutionBudget executionBudget) {
        return AgentToolRuntimeConfig.builder()
                .tools(List.of())
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId("loop-test")
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxModelCalls(maxModelCalls)
                .maxToolCalls(maxToolCalls)
                .maxInputTokens(maxInputTokens)
                .maxOutputTokens(maxOutputTokens)
                .maxTotalTokens(maxTotalTokens)
                .maxFinalizationTokens(maxFinalizationTokens)
                .maxResultCharacters(1000)
                .executionBudget(executionBudget)
                .build();
    }
}
