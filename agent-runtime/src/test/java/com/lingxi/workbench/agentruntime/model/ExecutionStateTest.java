package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ExecutionStateTest {

    @Test
    void shouldFinalizeWhenTokenSoftLimitIsReachedAndReserveFinalCall() {
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(sink, 4, 4, 80, 100);
        int callNo = state.beforeModelCall();

        state.completeModelCall(callNo, TokenUsage.of(70, 10, 80), false);

        assertTrue(state.shouldFinalize());
        int finalCallNo = state.beforeFinalModelCall();
        assertEquals(2, finalCallNo);
        assertEquals(List.of(ExecutionEventType.MODEL_STARTED, ExecutionEventType.MODEL_COMPLETED,
                        ExecutionEventType.FINALIZING, ExecutionEventType.MODEL_STARTED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldOnlyApproveTwoCallsForSameToolPerExecution() {
        ExecutionState state = state(new RecordingSink(), 4, 4, 100, 120);

        ExecutionState.ToolCallSelection first = state.selectToolCalls(List.of(toolCall("call-1")));
        ExecutionState.ToolCallSelection second = state.selectToolCalls(List.of(
                toolCall("call-2"), toolCall("call-3")));

        assertEquals(1, first.approvedCalls().size());
        assertEquals(1, second.approvedCalls().size());
        assertEquals(2, state.toolCalls());
        assertTrue(second.limited());
        assertTrue(state.shouldFinalize());
    }

    @Test
    void shouldRejectUnknownToolWithoutExecutingIt() {
        ExecutionState state = state(new RecordingSink(), 4, 4, 100, 120);

        ExecutionState.ToolCallSelection selection = state.selectToolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "unknown_tool", "{}")));

        assertTrue(selection.approvedCalls().isEmpty());
        assertTrue(selection.limited());
        assertEquals(0, state.toolCalls());
    }

    @Test
    void shouldRejectWorkingModelCallThatConsumesFinalReservation() {
        ExecutionState state = state(new RecordingSink(), 2, 2, 100, 120);
        int first = state.beforeModelCall();
        state.completeModelCall(first, TokenUsage.empty(), false);

        assertThrows(AgentRuntimeLimitException.class, state::beforeModelCall);
        assertEquals(2, state.beforeFinalModelCall());
    }

    private static ExecutionState state(RecordingSink sink, int maxModelCalls, int maxToolCalls,
                                        int maxTotalTokens, int maxFinalizationTokens) {
        AgentTool tool = tool();
        AgentToolRuntimeConfig config = AgentToolRuntimeConfig.builder()
                .tools(List.of(tool))
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId("state-test")
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxResultCharacters(1000)
                .build();
        return ExecutionState.builder()
                .runId("state-test")
                .agentId(10L)
                .agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(maxModelCalls)
                        .maxToolCalls(maxToolCalls)
                        .maxTotalTokens(maxTotalTokens)
                        .maxFinalizationTokens(maxFinalizationTokens)
                        .timeout(Duration.ofSeconds(2))
                        .activeOutputGrace(Duration.ZERO)
                        .build())
                .eventSink(sink)
                .toolRuntimeConfig(config)
                .authorizedToolCodes(List.of("test_tool"))
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

    private static final class RecordingSink implements RuntimeEventSink {
        private final List<ExecutionEvent> events = new ArrayList<>();

        @Override
        public void onEvent(ExecutionEvent event) {
            events.add(event);
        }

        @Override
        public void onText(ChatStreamChunk chunk) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
