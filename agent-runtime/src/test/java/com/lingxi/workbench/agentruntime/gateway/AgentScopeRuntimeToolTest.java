package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentScopeRuntimeToolTest {

    @Test
    void shouldInvokePublishedToolThroughToolkitAndRecordLingxiEvents() {
        AtomicReference<String> input = new AtomicReference<>();
        AgentTool tool = tool("weather_query", value -> {
            input.set(value);
            return "晴，24℃";
        });
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(tool, sink, null);
        AgentScopeRuntimeTool runtimeTool = new AgentScopeRuntimeTool(tool,
                state.toolRuntimeConfig().getExecutionContext(), 100, state);

        ToolResultBlock result = runtimeTool.callAsync(call("tool-1", "weather_query", Map.of("city", "上海"),
                RuntimeContext.builder().put(ExecutionState.class, state).build())).block();

        assertEquals("tool-1", result.getId());
        assertEquals("weather_query", result.getName());
        assertEquals("{\"city\":\"上海\"}", input.get());
        assertEquals(1, state.toolCalls());
        assertEquals(List.of(ExecutionEventType.TOOL_STARTED, ExecutionEventType.TOOL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldCompleteOnlyTheExactApprovedToolInvocation() {
        AgentTool tool = tool("weather_query", ignored -> "晴");
        AgentScopeCheckpointStore checkpointStore = mock(AgentScopeCheckpointStore.class);
        ExecutionState state = state(tool, new RecordingSink(), checkpointStore);
        AgentScopeCheckpoint.ToolCallSnapshot approvedCall = new AgentScopeCheckpoint.ToolCallSnapshot(
                "tool-2", "weather_query", Map.of("city", "杭州"));
        RuntimeContext approvedContext = RuntimeContext.builder()
                .put(ExecutionState.class, state)
                .put(AgentScopeApprovedInvocation.class, new AgentScopeApprovedInvocation(81L, "approval-event", approvedCall))
                .build();
        AgentScopeRuntimeTool runtimeTool = new AgentScopeRuntimeTool(tool,
                state.toolRuntimeConfig().getExecutionContext(), 100, state);

        runtimeTool.callAsync(call("tool-2", "weather_query", Map.of("city", "杭州"), approvedContext)).block();

        verify(checkpointStore).complete(81L, "晴");
        assertThrows(IllegalStateException.class, () -> runtimeTool.callAsync(
                call("tool-3", "weather_query", Map.of("city", "宁波"), approvedContext)).block());
        verify(checkpointStore).complete(81L, "晴");
    }

    private static ToolCallParam call(String id, String name, Map<String, Object> input,
                                      RuntimeContext context) {
        return ToolCallParam.builder()
                .toolUseBlock(new ToolUseBlock(id, name, input))
                .input(input)
                .runtimeContext(context)
                .build();
    }

    private static ExecutionState state(AgentTool tool, RecordingSink sink,
                                        AgentScopeCheckpointStore checkpointStore) {
        return ExecutionState.builder()
                .runId("runtime-tool-test")
                .agentId(10L)
                .agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(2)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(100)
                        .build())
                .eventSink(sink)
                .toolRuntimeConfig(AgentToolRuntimeConfig.builder()
                        .tools(List.of(tool))
                        .executionContext(AgentToolExecutionContext.builder()
                                .executionId("execution-1")
                                .userId(1L)
                                .conversationId(2L)
                                .assistantMessageId(3L)
                                .build())
                        .maxResultCharacters(100)
                        .build())
                .authorizedToolCodes(List.of("weather_query"))
                .checkpointStore(checkpointStore)
                .build();
    }

    private static AgentTool tool(String code, java.util.function.Function<String, String> handler) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code(code)
                .displayName("查询天气")
                .description("查询指定城市天气")
                .build();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(code)
                        .description("查询指定城市天气")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                return handler.apply(toolInput);
            }
        };
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
