package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;

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
        Fixture fixture = fixture(tool, sink);
        AgentScopeRuntimeTool runtimeTool = new AgentScopeRuntimeTool(tool,
                fixture.config().getExecutionContext(), 100, null);

        ToolResultBlock result = runtimeTool.callAsync(call("tool-1", "weather_query", Map.of("city", "上海"),
                fixture.context())).block();

        assertEquals("tool-1", result.getId());
        assertEquals("weather_query", result.getName());
        assertEquals("{\"city\":\"上海\"}", input.get());
        assertEquals(1, fixture.budget().toolCalls());
        assertEquals(List.of(ExecutionEventType.TOOL_STARTED, ExecutionEventType.TOOL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldCompleteOnlyTheExactApprovedToolInvocation() {
        AgentTool tool = tool("weather_query", ignored -> "晴");
        AgentScopeCheckpointStore store = mock(AgentScopeCheckpointStore.class);
        Fixture fixture = fixture(tool, new RecordingSink());
        AgentScopeCheckpoint.ToolCallSnapshot approvedCall = new AgentScopeCheckpoint.ToolCallSnapshot(
                "tool-2", "weather_query", Map.of("city", "杭州"));
        fixture.context().put(AgentScopeApprovedInvocation.class,
                new AgentScopeApprovedInvocation(81L, "approval-event", approvedCall));
        AgentScopeRuntimeTool runtimeTool = new AgentScopeRuntimeTool(tool,
                fixture.config().getExecutionContext(), 100, store);

        runtimeTool.callAsync(call("tool-2", "weather_query", Map.of("city", "杭州"), fixture.context())).block();

        verify(store).complete(81L, "晴");
        assertThrows(IllegalStateException.class, () -> runtimeTool.callAsync(
                call("tool-3", "weather_query", Map.of("city", "宁波"), fixture.context())).block());
        verify(store).complete(81L, "晴");
    }

    private static ToolCallParam call(String id, String name, Map<String, Object> input, RuntimeContext context) {
        return ToolCallParam.builder().toolUseBlock(new ToolUseBlock(id, name, input))
                .input(input).runtimeContext(context).build();
    }

    private static Fixture fixture(AgentTool tool, RecordingSink sink) {
        ExecutionPolicy policy = ExecutionPolicy.builder().maxModelCalls(2).maxToolCalls(2)
                .maxTotalTokens(100).maxFinalizationTokens(100).build();
        AgentToolRuntimeConfig config = AgentToolRuntimeConfig.builder().tools(List.of(tool))
                .executionContext(AgentToolExecutionContext.builder().executionId("execution-1")
                        .userId(1L).conversationId(2L).assistantMessageId(3L).build())
                .maxResultCharacters(100).build();
        ExecutionBudget budget = new ExecutionBudget(policy, 0, 0, TokenUsage.empty());
        RuntimeContext context = RuntimeContext.builder().build();
        context.put(ExecutionBudget.class, budget);
        context.put(ExecutionTools.class, new ExecutionTools(config, List.of("weather_query"), policy));
        context.put(ExecutionEvents.class, new ExecutionEvents("runtime-tool-test", 10L, 20L,
                ExecutionScope.RUN, sink, 0L));
        return new Fixture(config, budget, context);
    }

    private static AgentTool tool(String code, java.util.function.Function<String, String> handler) {
        AgentToolDefinition definition = AgentToolDefinition.builder().code(code).displayName("查询天气")
                .description("查询指定城市天气").build();
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")));
            }

            @Override
            public String execute(AgentToolRequest request) {
                return handler.apply(request.input());
            }
        };
    }

    private record Fixture(AgentToolRuntimeConfig config, ExecutionBudget budget, RuntimeContext context) {
    }

    private static final class RecordingSink implements RuntimeEventSink {
        private final List<ExecutionEvent> events = new ArrayList<>();

        @Override
        public long onEvent(ExecutionEvent event) {
            events.add(event);
            return event.getSequenceNo();
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
