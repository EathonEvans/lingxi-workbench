package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.WorkflowChatResult;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ChatWorkflowExecutionRegistry;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.workflow.exception.WorkflowExecutionException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationWorkflowChatServiceImplTest {

    @Test
    void shouldExecutePublishedSnapshotAndReturnResponse() {
        AtomicReference<String> executionId = new AtomicReference<>();
        ChatWorkflowExecutionRegistry registry =
                new ChatWorkflowExecutionRegistry();
        WorkflowExecutor executor = (definition, request) -> {
            executionId.set(request.executionId());
            assertEquals("问题", request.inputs().get("query"));
            assertEquals("30", request.applicationVersionId());
            assertEquals(
                    "测试用户",
                    registry.require(request.executionId())
                            .runtimeVariables()
                            .require(RuntimeVariableAccessors.USER_NAME));
            return new WorkflowExecutionResult(
                    request.executionId(), Map.of("response", "工作流回答"), List.of());
        };
        ApplicationWorkflowChatServiceImpl service =
                new ApplicationWorkflowChatServiceImpl(
                        executor,
                        agentRuntimeService(),
                        registry,
                        new RuntimeVariablesInitializer("Lingxi Workbench"));

        WorkflowChatResult result = service.execute(
                snapshot(),
                "问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3));

        assertEquals("工作流回答", result.answer());
        assertThrows(
                WorkflowExecutionException.class,
                () -> registry.require(executionId.get()));
    }

    @Test
    void shouldResolveEntryAgentLockedModel() {
        ApplicationWorkflowChatServiceImpl service =
                new ApplicationWorkflowChatServiceImpl(
                        (definition, request) -> null,
                        agentRuntimeService(),
                        new ChatWorkflowExecutionRegistry(),
                        new RuntimeVariablesInitializer("Lingxi Workbench"));

        assertEquals(99L, service.resolveEntryModelInstanceId(snapshot()));
    }

    private static AgentRuntimeService agentRuntimeService() {
        AgentRuntimeDefinition definition = AgentRuntimeDefinition.builder()
                .schemaVersion(1)
                .agentType("RAG_CHAT")
                .chatModelInstanceId(99L)
                .answerPromptVersionId(88L)
                .build();
        return (agentId, versionId) ->
                new AgentRuntimeSnapshot(agentId, versionId, definition);
    }

    private static ApplicationRuntimeSnapshot snapshot() {
        WorkflowNodeDefinition agent = new WorkflowNodeDefinition(
                "agent",
                "Agent",
                WorkflowNodeType.AGENT,
                new AgentNodeConfig(
                        "10",
                        "20",
                        Map.of("query", "query"),
                        Map.of("answer", "answer")));
        WorkflowDefinition workflow =
                new WorkflowDefinition(1, "main", List.of(agent), List.of());
        return new ApplicationRuntimeSnapshot(
                1L, "support", 30L, new ApplicationDefinition(1, workflow));
    }
}
