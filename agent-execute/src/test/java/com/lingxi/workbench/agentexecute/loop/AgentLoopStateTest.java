package com.lingxi.workbench.agentexecute.loop;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentExecutionBudget;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
                com.lingxi.workbench.agentexecute.loop.AgentLoopLimitException.class, state::beginModelCall);
    }

    @Test
    void shouldOnlyApproveTwoCallsForSameToolPerExecution() {
        AgentLoopState state = new AgentLoopState(runtimeConfig(10, 10, 100, 100));
        AgentLoopState.ToolCallSelection first = state.selectToolCalls(List.of(toolCall("call-1")));
        AgentLoopState.ToolCallSelection second = state.selectToolCalls(List.of(
                toolCall("call-2"), toolCall("call-3")));

        assertEquals(1, first.approvedCalls().size());
        assertEquals(1, second.approvedCalls().size());
        assertTrue(second.limited());
        assertTrue(state.shouldFinalize());
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
        AgentLoopState.ToolCallSelection selection = state.selectToolCalls(List.of(
                toolCall("call-1"), toolCall("call-2")));

        assertEquals(2, selection.approvedCalls().size());
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
                .tools(List.of(tool()))
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

    private static AssistantMessage.ToolCall toolCall(String id) {
        return new AssistantMessage.ToolCall(id, "function", "test_tool", "{}");
    }

    private static AgentTool tool() {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("test_tool")
                .displayName("测试工具")
                .description("测试工具调用边界")
                .build();
        ToolCallback callback = mock(ToolCallback.class);
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public ToolCallback callback() {
                return callback;
            }
        };
    }
}
