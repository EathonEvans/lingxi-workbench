package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;
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
    void shouldBoundSameToolCallsPerExecution() {
        ExecutionState state = state(new RecordingSink(), 4, 4, 100, 120);

        AgentToolDefinition definition = state.requireToolDefinition("test_tool");
        state.beforeModelCall();
        state.beforeToolCall(definition);
        state.beforeToolCall(definition);

        assertEquals(2, state.toolCalls());
        assertThrows(AgentRuntimeLimitException.class, () -> state.beforeToolCall(definition));
        assertTrue(state.shouldFinalize());
    }

    @Test
    void shouldRejectUnknownToolWithoutExecutingIt() {
        ExecutionState state = state(new RecordingSink(), 4, 4, 100, 120);

        AgentToolDefinition unknown = AgentToolDefinition.builder()
                .code("unknown_tool")
                .displayName("未知工具")
                .description("未授权工具")
                .build();

        assertThrows(IllegalArgumentException.class, () -> state.beforeToolCall(unknown));
        assertEquals(0, state.toolCalls());
    }

    @Test
    void shouldAllowAgentScopeToolLoopToUseTheWholeModelBudget() {
        ExecutionState state = state(new RecordingSink(), 2, 2, 100, 120);
        int first = state.beforeModelCall();
        state.completeModelCall(first, TokenUsage.empty(), false);

        assertEquals(2, state.beforeModelCall());
        assertThrows(AgentRuntimeLimitException.class, state::beforeModelCall);
    }

    @Test
    void shouldPublishSkillActivationWithoutSavingASecondRuntimeState() {
        AgentSkillBindingSnapshot binding = skillBinding(SkillActivationMode.MODEL_ROUTED);
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(sink, 4, 4, 100, 120);

        state.recordSkillActivation(binding, SkillActivationReason.MODEL_ROUTED);

        assertEquals(ExecutionEventType.SKILL_ACTIVATED, sink.events.getFirst().getType());
        assertEquals(SkillActivationReason.MODEL_ROUTED,
                ((com.lingxi.workbench.agentruntime.event.ExecutionEventPayload.Skill) sink.events.getFirst()
                        .getPayload()).getReason());
        assertTrue(state.checkpointToolCallCounts().isEmpty());
    }

    @Test
    void shouldRejectSkillAuditReasonThatDoesNotMatchFrozenActivationMode() {
        AgentSkillBindingSnapshot binding = skillBinding(SkillActivationMode.EXPLICIT_ONLY);

        assertThrows(IllegalArgumentException.class, () -> state(new RecordingSink(), 4, 4, 100, 120)
                .recordSkillActivation(binding, SkillActivationReason.MODEL_ROUTED));
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
                        .build())
                .eventSink(sink)
                .toolRuntimeConfig(config)
                .authorizedToolCodes(List.of("test_tool"))
                .build();
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

    private static AgentSkillBindingSnapshot skillBinding(SkillActivationMode mode) {
        return AgentSkillBindingSnapshot.builder().skillId(1L).skillVersionId(11L).versionNo(1)
                .checksum("checksum")
                .skill(SkillVersionSnapshot.builder().schemaVersion(1).code("fact_check").name("事实核验")
                        .description("核验事实").instructions("fact_check-instructions")
                        .activationMode(mode).requestedToolCodes(List.of()).build())
                .effectiveToolCodes(List.of()).deniedToolCodes(List.of()).build();
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
