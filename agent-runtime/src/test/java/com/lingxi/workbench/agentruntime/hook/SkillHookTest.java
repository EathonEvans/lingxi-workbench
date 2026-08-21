package com.lingxi.workbench.agentruntime.hook;

import com.lingxi.workbench.agentruntime.SkillRouter;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillHookTest {

    @Test
    void shouldActivateAlwaysExplicitAndModelRoutedSkillsInOneState() {
        AgentSkillBindingSnapshot always = binding(1L, "always", SkillActivationMode.ALWAYS_ON);
        AgentSkillBindingSnapshot explicit = binding(2L, "explicit", SkillActivationMode.EXPLICIT_ONLY);
        AgentSkillBindingSnapshot routed = binding(3L, "routed", SkillActivationMode.MODEL_ROUTED);
        SkillRouter router = mock(SkillRouter.class);
        when(router.route(any(), any(), any())).thenReturn(List.of("routed"));
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(sink);

        new SkillHook(router).beforeAgent(state,
                new SkillRuntimeCatalog(List.of(always, explicit, routed), List.of("explicit")), command(), true);

        assertTrue(state.skillInstructions().contains("always-instructions"));
        assertTrue(state.skillInstructions().contains("explicit-instructions"));
        assertTrue(state.skillInstructions().contains("routed-instructions"));
        assertEquals(List.of(SkillActivationReason.ALWAYS_ON, SkillActivationReason.EXPLICIT,
                        SkillActivationReason.MODEL_ROUTED),
                sink.events.stream().map(ExecutionEvent::getPayload)
                        .map(ExecutionEventPayload.Skill.class::cast)
                        .map(ExecutionEventPayload.Skill::getReason).toList());
    }

    @Test
    void shouldSkipModelRoutingDuringFinalizationWithoutDroppingDeterministicSkills() {
        AgentSkillBindingSnapshot always = binding(1L, "always", SkillActivationMode.ALWAYS_ON);
        AgentSkillBindingSnapshot routed = binding(2L, "routed", SkillActivationMode.MODEL_ROUTED);
        SkillRouter router = mock(SkillRouter.class);
        ExecutionState state = state(new RecordingSink());

        new SkillHook(router).beforeAgent(state,
                new SkillRuntimeCatalog(List.of(always, routed), List.of()), command(), false);

        assertTrue(state.skillInstructions().contains("always-instructions"));
        org.junit.jupiter.api.Assertions.assertFalse(state.skillInstructions().contains("routed-instructions"));
        verify(router, never()).route(any(), any(), any());
    }

    private static ExecutionState state(RecordingSink sink) {
        return ExecutionState.builder()
                .runId("skill-hook-run").agentId(10L).agentVersionId(20L)
                .policy(ExecutionPolicy.builder().maxModelCalls(3).maxToolCalls(0)
                        .maxTotalTokens(100).maxFinalizationTokens(100)
                        .timeout(Duration.ofSeconds(3)).activeOutputGrace(Duration.ZERO).build())
                .eventSink(sink).authorizedToolCodes(List.of()).build();
    }

    private static ChatStreamCommand command() {
        return ChatStreamCommand.builder().prompt("处理任务").systemPrompt("你是助手")
                .history(List.of()).modelConfig(ChatModelRuntimeConfig.builder().build()).build();
    }

    private static AgentSkillBindingSnapshot binding(Long id, String code, SkillActivationMode mode) {
        return AgentSkillBindingSnapshot.builder().skillId(id).skillVersionId(id + 10).versionNo(1)
                .checksum("checksum-" + code)
                .skill(SkillVersionSnapshot.builder().schemaVersion(1).code(code).name(code)
                        .description(code + "-description").instructions(code + "-instructions")
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
