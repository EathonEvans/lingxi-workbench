package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.ApplicationRun;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAgentNodeInvokerTest {

    @Test
    void shouldOnlyAdaptWorkflowInvocationToAgentExecutionAdapter() {
        ApplicationRun run = mock(ApplicationRun.class);
        AtomicReference<ApplicationRun> capturedRun = new AtomicReference<>();
        AgentExecutionAdapter agentExecutionAdapter = mock(AgentExecutionAdapter.class);
        when(agentExecutionAdapter.run(eq(10L), eq(20L), eq("问题"), any(ApplicationRun.class), eq(null)))
                .thenAnswer(invocation -> {
                    capturedRun.set(invocation.getArgument(3));
                    return new com.lingxi.workbench.agentruntime.model.AgentRunResult.Completed(
                            "Agent回答", com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage.empty(), 0L);
                });
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(agentExecutionAdapter);

        Map<String, Object> output = invoker.invoke(invocation("问题"), run, null);

        assertEquals("Agent回答", output.get("answer"));
        assertEquals(run, capturedRun.get());
    }

    @Test
    void shouldRejectBlankWorkflowQueryBeforeCallingAgent() {
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(mock(AgentExecutionAdapter.class));
        ApplicationRun run = mock(ApplicationRun.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoker.invoke(invocation(" "), run, null));

        assertEquals(
                "Agent节点输入query必须是非空字符串: agent-node",
                exception.getMessage());
    }

    private static AgentNodeInvocation invocation(String query) {
        return AgentNodeInvocation.builder()
                .executionId("run-1")
                .workflowVersionId(30L)
                .nodeId("agent-node")
                .agentId(10L)
                .agentVersionId(20L)
                .inputs(Map.of("query", query))
                .build();
    }
}
