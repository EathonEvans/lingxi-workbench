package com.lingxi.workbench.agentruntime.loop;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointStore;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeToolCallbackTest {

    @Test
    void shouldCompleteApprovedInvocationAndPublishUnifiedEvents() {
        ToolCallback delegate = mock(ToolCallback.class);
        RuntimeCheckpointStore store = mock(RuntimeCheckpointStore.class);
        AgentTool tool = tool(delegate);
        RecordingSink sink = new RecordingSink();
        when(delegate.call("{}")).thenReturn("done");
        ExecutionState state = state(tool, sink, store);
        state.prepareApprovedInvocation(19L, RuntimeCheckpoint.ToolCallSnapshot.builder()
                .id("call-1").type("function").name("send_mail").arguments("{}").build());
        RuntimeToolCallback callback = callback(tool, state);

        String result = callback.call("{}");

        assertEquals("done", result);
        verify(store).complete(19L, "done");
        verify(store, never()).fail(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(RuntimeException.class));
        assertEquals(List.of(ExecutionEventType.TOOL_STARTED, ExecutionEventType.TOOL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldNotMarkExternalInvocationFailedWhenCheckpointPersistenceFails() {
        ToolCallback delegate = mock(ToolCallback.class);
        RuntimeCheckpointStore store = mock(RuntimeCheckpointStore.class);
        AgentTool tool = tool(delegate);
        when(delegate.call("{}")).thenReturn("done");
        doThrow(new IllegalStateException("persist failed")).when(store).complete(19L, "done");
        ExecutionState state = state(tool, new RecordingSink(), store);
        state.prepareApprovedInvocation(19L, RuntimeCheckpoint.ToolCallSnapshot.builder()
                .id("call-1").type("function").name("send_mail").arguments("{}").build());
        RuntimeToolCallback callback = callback(tool, state);

        assertThrows(AgentReconciliationPendingException.class, () -> callback.call("{}"));

        verify(store).markReconciliationPending(
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.any(RuntimeException.class));
        verify(store, never()).fail(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(RuntimeException.class));
    }

    private static RuntimeToolCallback callback(AgentTool tool, ExecutionState state) {
        return new RuntimeToolCallback(tool, executionContext(), 1000, state);
    }

    private static ExecutionState state(AgentTool tool, RecordingSink sink, RuntimeCheckpointStore store) {
        AgentToolRuntimeConfig config = AgentToolRuntimeConfig.builder()
                .tools(List.of(tool))
                .executionContext(executionContext())
                .maxResultCharacters(1000)
                .build();
        return ExecutionState.builder()
                .runId("run-1")
                .agentId(10L)
                .agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(3)
                        .maxToolCalls(2)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(120)
                        .timeout(Duration.ofSeconds(2))
                        .activeOutputGrace(Duration.ZERO)
                        .build())
                .eventSink(sink)
                .toolRuntimeConfig(config)
                .authorizedToolCodes(List.of("send_mail"))
                .checkpointStore(store)
                .build();
    }

    private static AgentToolExecutionContext executionContext() {
        return AgentToolExecutionContext.builder()
                .executionId("run-1")
                .userId(7L)
                .conversationId(8L)
                .assistantMessageId(9L)
                .build();
    }

    private static AgentTool tool(ToolCallback callback) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("send_mail")
                .displayName("发送邮件")
                .description("发送一封外部邮件")
                .build();
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name(definition.getCode())
                .description(definition.getDescription())
                .inputSchema("{\"type\":\"object\"}")
                .build());
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
