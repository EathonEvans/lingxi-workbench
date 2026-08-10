package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.AgentLoopLimitException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopStateTest {

    @Test
    void shouldRequestFinalAnswerWhenTokenWarningThresholdIsReached() {
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 5, 100, 100));

        state.beginModelCall();
        state.recordUsage(TokenUsage.of(70, 10, 80));

        assertTrue(state.shouldFinalize());
    }

    @Test
    void shouldRejectModelCallAfterInputTokenLimit() {
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 5, 100, 100));
        state.recordUsage(TokenUsage.of(100, 5, 105));

        AgentLoopLimitException exception = assertThrows(AgentLoopLimitException.class, state::beginModelCall);

        assertEquals("AGENT_INPUT_TOKEN_LIMIT", exception.getErrorCode());
    }

    @Test
    void shouldRejectToolBatchBeyondRemainingBudget() {
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 2, 100, 100));
        state.recordToolCalls(1);

        AgentLoopLimitException exception = assertThrows(AgentLoopLimitException.class, () -> state.recordToolCalls(2));

        assertEquals("AGENT_TOOL_CALL_LIMIT", exception.getErrorCode());
    }

    private static AgentToolRuntimeConfig runtimeConfig(int maxModelCalls, int maxToolCalls, int maxInputTokens, int maxOutputTokens) {
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
                .maxResultCharacters(1000)
                .build();
    }
}
