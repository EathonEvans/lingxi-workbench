package com.lingxi.workbench.agentruntime.hook;

import com.lingxi.workbench.agentruntime.checkpoint.HumanApprovalRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointContext;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.checkpoint.SpringAiCheckpointCodec;
import com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.model.ExecutionStatus;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import com.lingxi.workbench.infra.ai.tool.model.ToolSecurityPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanApprovalHookTest {

    @Test
    void shouldPersistOriginalSpringAiCallBeforeInterruptingRuntime() {
        RuntimeCheckpointStore store = mock(RuntimeCheckpointStore.class);
        when(store.interrupt(org.mockito.ArgumentMatchers.any())).thenReturn(RuntimeInterruption.builder()
                .interactionId(91L).checkpointId(92L).fingerprint("approved").build());
        RecordingSink sink = new RecordingSink();
        AgentTool tool = approvalTool();
        ExecutionState state = state(tool, store, sink);
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall(
                "call-1", "function", "send_mail", "{\"target\":\"a@b.com\"}");
        state.selectToolCalls(List.of(call));
        ChatResponse response = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("").toolCalls(List.of(call)).build())));
        Prompt prompt = new Prompt(List.of(new SystemMessage("系统"), new UserMessage("发送邮件")));

        HumanApprovalHook hook = new HumanApprovalHook(
                new SpringAiCheckpointCodec(), new ToolApprovalFingerprint());
        assertTrue(hook.beforeTool(prompt, response, state).isPresent());

        ArgumentCaptor<HumanApprovalRequest> captor = ArgumentCaptor.forClass(HumanApprovalRequest.class);
        verify(store).interrupt(captor.capture());
        assertEquals("call-1", captor.getValue().checkpoint().getPendingToolCall().getId());
        assertEquals("send_mail", captor.getValue().checkpoint().getPendingToolCall().getName());
        assertEquals(2, captor.getValue().checkpoint().getMessages().size());
        assertEquals(1, captor.getValue().checkpoint().getState().getToolCalls());
        assertEquals(ExecutionStatus.INTERRUPTED, state.getStatus());
        assertEquals(ExecutionEventType.HUMAN_REQUIRED, sink.events.getLast().getType());
    }

    private static ExecutionState state(AgentTool tool, RuntimeCheckpointStore store, RecordingSink sink) {
        AgentToolExecutionContext toolContext = AgentToolExecutionContext.builder()
                .executionId("run-1").userId(7L).conversationId(8L).assistantMessageId(9L).build();
        return ExecutionState.builder()
                .runId("run-1")
                .agentId(10L)
                .agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(3).maxToolCalls(2).maxTotalTokens(100)
                        .maxFinalizationTokens(120).timeout(Duration.ofSeconds(10))
                        .activeOutputGrace(Duration.ZERO).build())
                .eventSink(sink)
                .toolRuntimeConfig(AgentToolRuntimeConfig.builder()
                        .tools(List.of(tool)).executionContext(toolContext).maxResultCharacters(1000).build())
                .authorizedToolCodes(List.of("send_mail"))
                .checkpointContext(RuntimeCheckpointContext.builder()
                        .query("发送邮件").capabilityCode("mail_agent").applicationVersionId(3L)
                        .userId(7L).conversationId(8L).assistantMessageId(9L).build())
                .checkpointStore(store)
                .build();
    }

    private static AgentTool approvalTool() {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("send_mail")
                .displayName("发送邮件")
                .description("发送外部邮件")
                .configurationHash("mail-v1")
                .securityPolicy(ToolSecurityPolicy.builder()
                        .approvalRequired(true).approvalTtlSeconds(300)
                        .riskLevel(ToolRiskLevel.HIGH).resourceScope("用户邮箱").build())
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
