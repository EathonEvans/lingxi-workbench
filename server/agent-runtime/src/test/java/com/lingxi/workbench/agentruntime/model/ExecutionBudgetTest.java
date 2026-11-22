package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
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
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionBudgetTest {

    @Test
    void shouldFinalizeWhenTokenSoftLimitIsReachedAndReserveFinalCall() {
        ExecutionBudget budget = budget(4, 4, 80, 100);
        int callNo = budget.reserveModelCall();
        budget.completeModelCall(TokenUsage.of(70, 10, 80));

        assertTrue(budget.shouldFinalize());
        assertTrue(budget.requestFinalization("Token软额度已达到上限"));
        assertEquals(2, budget.reserveFinalModelCall());
        assertEquals(1, callNo);
    }

    @Test
    void shouldBoundSameToolCallsPerExecution() {
        ExecutionBudget budget = budget(4, 4, 100, 120);
        ExecutionTools tools = tools(budget.policy());
        AgentToolDefinition definition = tools.requireDefinition("test_tool");

        tools.startModelRound();
        tools.reserve(definition, budget);
        tools.reserve(definition, budget);

        assertEquals(2, budget.toolCalls());
        assertThrows(AgentRuntimeLimitException.class, () -> tools.reserve(definition, budget));
        assertTrue(budget.isFinalizationRequested());
    }

    @Test
    void shouldRejectUnknownToolWithoutConsumingGlobalBudget() {
        ExecutionBudget budget = budget(4, 4, 100, 120);
        ExecutionTools tools = tools(budget.policy());
        AgentToolDefinition unknown = AgentToolDefinition.builder().code("unknown_tool")
                .displayName("未知工具").description("未授权工具").build();

        assertThrows(IllegalArgumentException.class, () -> tools.reserve(unknown, budget));
        assertEquals(0, budget.toolCalls());
    }

    @Test
    void shouldAllowAgentScopeLoopToUseWholeModelBudget() {
        ExecutionBudget budget = budget(2, 2, 100, 120);
        assertEquals(1, budget.reserveModelCall());
        budget.completeModelCall(TokenUsage.empty());

        assertEquals(2, budget.reserveModelCall());
        assertThrows(AgentRuntimeLimitException.class, budget::reserveModelCall);
    }

    @Test
    void shouldProjectSkillActivationWithoutSecondActivationState() {
        RecordingSink sink = new RecordingSink();
        ExecutionEvents events = new ExecutionEvents("run", 10L, 20L, ExecutionScope.RUN, sink, 0L);
        AgentSkillBindingSnapshot binding = skillBinding(SkillActivationMode.MODEL_ROUTED);

        events.skillActivated(binding, SkillActivationReason.MODEL_ROUTED);

        assertEquals(ExecutionEventType.SKILL_ACTIVATED, sink.events.getFirst().getType());
        assertEquals(SkillActivationReason.MODEL_ROUTED,
                ((ExecutionEventPayload.Skill) sink.events.getFirst().getPayload()).getReason());
    }

    @Test
    void shouldRejectSkillReasonThatDoesNotMatchPublishedMode() {
        ExecutionEvents events = new ExecutionEvents("run", 10L, 20L, ExecutionScope.RUN,
                new RecordingSink(), 0L);

        assertThrows(IllegalArgumentException.class, () -> events.skillActivated(
                skillBinding(SkillActivationMode.EXPLICIT_ONLY), SkillActivationReason.MODEL_ROUTED));
    }

    @Test
    void shouldOnlyTreatExplicitSinkStateAsCancellation() {
        ExecutionEvents events = new ExecutionEvents("run", 10L, 20L, ExecutionScope.RUN,
                new RecordingSink(), 0L);

        Thread.currentThread().interrupt();
        try {
            assertFalse(events.isCancelled());
        } finally {
            Thread.interrupted();
        }
    }

    private static ExecutionBudget budget(int models, int tools, int totalTokens, int finalTokens) {
        return new ExecutionBudget(ExecutionPolicy.builder().maxModelCalls(models).maxToolCalls(tools)
                .maxTotalTokens(totalTokens).maxFinalizationTokens(finalTokens).build(), 0, 0,
                TokenUsage.empty());
    }

    private static ExecutionTools tools(ExecutionPolicy policy) {
        AgentToolRuntimeConfig config = AgentToolRuntimeConfig.builder().tools(List.of(tool()))
                .executionContext(AgentToolExecutionContext.builder().executionId("budget-test")
                        .userId(1L).conversationId(2L).assistantMessageId(3L).build())
                .maxResultCharacters(1000).build();
        return new ExecutionTools(config, List.of("test_tool"), policy);
    }

    private static AgentTool tool() {
        AgentToolDefinition definition = AgentToolDefinition.builder().code("test_tool")
                .displayName("测试工具").description("测试工具调用边界").build();
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public String execute(AgentToolRequest request) {
                return "ok";
            }
        };
    }

    private static AgentSkillBindingSnapshot skillBinding(SkillActivationMode mode) {
        return AgentSkillBindingSnapshot.builder().skillId(1L).skillVersionId(11L).versionNo(1)
                .checksum("checksum").skill(SkillVersionSnapshot.builder().schemaVersion(1).code("fact_check")
                        .name("事实核验").description("核验事实").instructions("fact_check-instructions")
                        .activationMode(mode).requestedToolCodes(List.of()).build())
                .effectiveToolCodes(List.of()).deniedToolCodes(List.of()).build();
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
