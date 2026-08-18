package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;

import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.agent.AgentChatService;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionContextRegistry;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.context.LoginUser;
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
    void shouldOnlyAdaptWorkflowInvocationToAgentChatService() {
        ChatExecutionContext context = ChatExecutionContext.builder()
                .executionId("run-1")
                .runtimeVariables(new RuntimeVariablesInitializer("Lingxi Workbench")
                        .initialize(
                                new LoginUser(
                                        7L,
                                        "tester",
                                        "测试用户",
                                null),
                                "问题",
                                10L))
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();
        ChatExecutionContextRegistry registry =
                new ChatExecutionContextRegistry();
        registry.register(context);
        AtomicReference<ChatExecutionContext> capturedContext =
                new AtomicReference<>();
        AgentChatService agentChatService = mock(AgentChatService.class);
        when(agentChatService.chat(eq(10L), eq(20L), eq("问题"), any(ChatExecutionContext.class)))
                .thenAnswer(invocation -> {
                    capturedContext.set(invocation.getArgument(3));
                    return new com.lingxi.workbench.agentruntime.model.AgentRunResult.Completed(
                            "Agent回答", com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage.empty(), 0L);
                });
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(agentChatService, registry);

        Map<String, Object> output = invoker.invoke(invocation("问题"));

        assertEquals("Agent回答", output.get("answer"));
        assertEquals(context, capturedContext.get());
    }

    @Test
    void shouldRejectBlankWorkflowQueryBeforeCallingAgent() {
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(
                mock(AgentChatService.class), new ChatExecutionContextRegistry());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoker.invoke(invocation(" ")));

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
