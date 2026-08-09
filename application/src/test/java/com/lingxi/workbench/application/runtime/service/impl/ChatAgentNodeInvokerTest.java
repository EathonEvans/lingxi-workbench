package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(
                (agentId, agentVersionId, query, execution) -> {
                    assertEquals(10L, agentId);
                    assertEquals(20L, agentVersionId);
                    assertEquals("问题", query);
                    capturedContext.set(execution);
                    return "Agent回答";
                },
                registry);

        Map<String, Object> output = invoker.invoke(invocation("问题"));

        assertEquals("Agent回答", output.get("answer"));
        assertEquals(context, capturedContext.get());
    }

    @Test
    void shouldRejectBlankWorkflowQueryBeforeCallingAgent() {
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(
                (agentId, agentVersionId, query, execution) -> "不应调用",
                new ChatExecutionContextRegistry());

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
