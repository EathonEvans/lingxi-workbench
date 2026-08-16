package com.lingxi.workbench.agentexecute.loop;

import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolApprovalGate;
import com.lingxi.workbench.infra.ai.tool.AgentToolApprovalPermit;
import com.lingxi.workbench.infra.ai.tool.AgentToolEventPublisher;
import com.lingxi.workbench.infra.ai.tool.HumanInteractionRequiredException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoundedToolCallbackTest {

    @Test
    void shouldNotInvokeExternalToolBeforeHumanApproval() {
        ToolCallback delegate = mock(ToolCallback.class);
        AgentTool tool = tool(delegate);
        AgentLoopState loopState = new AgentLoopState(runtimeConfig(tool));
        AgentToolApprovalGate gate = request -> {
            throw new HumanInteractionRequiredException(19L);
        };
        BoundedToolCallback callback = new BoundedToolCallback(tool, executionContext(), 1000,
                AgentToolEventPublisher.noop(), loopState, gate);

        HumanInteractionRequiredException exception = assertThrows(
                HumanInteractionRequiredException.class, () -> callback.call("{\"target\":\"mail\"}"));

        assertEquals(19L, exception.interactionId());
        verify(delegate, never()).call("{\"target\":\"mail\"}");
    }

    @Test
    void shouldCompleteApprovalPermitAfterSuccessfulInvocation() {
        ToolCallback delegate = mock(ToolCallback.class);
        AgentToolApprovalPermit permit = mock(AgentToolApprovalPermit.class);
        AgentTool tool = tool(delegate);
        AgentLoopState loopState = new AgentLoopState(runtimeConfig(tool));
        when(delegate.call("{}")).thenReturn("done");
        BoundedToolCallback callback = new BoundedToolCallback(tool, executionContext(), 1000,
                AgentToolEventPublisher.noop(), loopState, request -> permit);

        String result = callback.call("{}");

        assertEquals("done", result);
        verify(permit).complete("done");
        verify(permit, never()).fail(org.mockito.ArgumentMatchers.any(RuntimeException.class));
    }

    @Test
    void shouldNotMarkExternalInvocationFailedWhenResultPersistenceFails() {
        ToolCallback delegate = mock(ToolCallback.class);
        AgentToolApprovalPermit permit = mock(AgentToolApprovalPermit.class);
        AgentTool tool = tool(delegate);
        AgentLoopState loopState = new AgentLoopState(runtimeConfig(tool));
        when(delegate.call("{}")).thenReturn("done");
        doThrow(new IllegalStateException("persist failed")).when(permit).complete("done");
        BoundedToolCallback callback = new BoundedToolCallback(tool, executionContext(), 1000,
                AgentToolEventPublisher.noop(), loopState, request -> permit);

        assertThrows(IllegalStateException.class, () -> callback.call("{}"));

        verify(permit, never()).fail(org.mockito.ArgumentMatchers.any(RuntimeException.class));
    }

    private static AgentToolRuntimeConfig runtimeConfig(AgentTool tool) {
        return AgentToolRuntimeConfig.builder()
                .tools(List.of(tool))
                .executionContext(executionContext())
                .maxModelCalls(3)
                .maxToolCalls(2)
                .maxResultCharacters(1000)
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
}
