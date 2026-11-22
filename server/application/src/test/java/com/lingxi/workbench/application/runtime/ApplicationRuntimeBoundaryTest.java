package com.lingxi.workbench.application.runtime;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutionAdapter;
import com.lingxi.workbench.application.runtime.service.app.AppRuntime;
import com.lingxi.workbench.application.runtime.service.capability.AppCapabilityDispatcher;
import com.lingxi.workbench.application.runtime.service.skill.SkillRuntimeService;
import com.lingxi.workbench.application.runtime.model.ApplicationRun;
import com.lingxi.workbench.chat.model.request.ChatRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationRuntimeBoundaryTest {

    private static final Set<String> RUNTIME_ENGINE_TYPES = Set.of(
            "org.springframework.ai.chat.client.ChatClient",
            "org.springframework.ai.chat.model.ChatModel",
            "org.springframework.ai.model.tool.ToolCallingManager");

    @Test
    void shouldKeepSpringAiExecutionEngineOutsideApplicationAdapters() {
        List<Class<?>> applicationAdapters = List.of(
                AppRuntime.class,
                AppCapabilityDispatcher.class,
                AgentExecutionAdapter.class);

        for (Class<?> adapter : applicationAdapters) {
            for (Field field : adapter.getDeclaredFields()) {
                assertFalse(RUNTIME_ENGINE_TYPES.contains(field.getType().getName()),
                        () -> adapter.getSimpleName() + "不得直接持有" + field.getType().getSimpleName());
            }
        }
    }

    @Test
    void shouldNotRestoreDeletedApplicationRuntimeCoordinators() {
        List<String> removedTypes = List.of(
                "com.lingxi.workbench.application.runtime.service.execution.ChatExecutionContextRegistry",
                "com.lingxi.workbench.application.runtime.model.ChatExecutionContext",
                "com.lingxi.workbench.application.runtime.service.plan.PlannerRuntimeCoordinator",
                "com.lingxi.workbench.application.runtime.service.plan.PlanExecutionService",
                "com.lingxi.workbench.application.runtime.service.plan.FinalSynthesisService",
                "com.lingxi.workbench.agentruntime.PlannerRuntime",
                "com.lingxi.workbench.agentruntime.SupervisorRuntime",
                "com.lingxi.workbench.agentruntime.plan.RuntimePlanStore",
                "com.lingxi.workbench.agentruntime.planner.PlannerDecision",
                "com.lingxi.workbench.agentruntime.model.ExecutionState",
                "com.lingxi.workbench.agentruntime.model.ExecutionStatus",
                "com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeContextAdvisor",
                "com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeModelAdvisor",
                "com.lingxi.workbench.agentruntime.gateway.SpringAiChatCompletionGateway",
                "com.lingxi.workbench.agentruntime.gateway.TokenUsageConverter",
                "com.lingxi.workbench.agentruntime.config.ToolCallingConfig",
                "com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimePlanStore",
                "com.lingxi.workbench.application.runtime.service.app.ApplicationChatExecutionService",
                "com.lingxi.workbench.application.runtime.service.app.ApplicationChatExecutionServiceImpl",
                "com.lingxi.workbench.application.runtime.service.app.ApplicationRuntimeService",
                "com.lingxi.workbench.application.runtime.service.app.ApplicationRuntimeServiceImpl",
                "com.lingxi.workbench.application.runtime.service.agent.AgentRuntimeService",
                "com.lingxi.workbench.application.runtime.service.agent.AgentRuntimeServiceImpl",
                "com.lingxi.workbench.application.runtime.service.agent.AgentChatService",
                "com.lingxi.workbench.application.runtime.service.agent.AgentChatServiceImpl",
                "com.lingxi.workbench.application.runtime.service.capability.CapabilityExecutionService",
                "com.lingxi.workbench.application.runtime.service.capability.CapabilityExecutionServiceImpl",
                "com.lingxi.workbench.application.runtime.service.agent.AgentExecutor",
                "com.lingxi.workbench.application.runtime.service.agent.AgentExecutorRegistry",
                "com.lingxi.workbench.application.runtime.service.agent.SpecialistAgentExecutor",
                "com.lingxi.workbench.application.runtime.service.plan.SupervisorAgentExecutor",
                "com.lingxi.workbench.application.runtime.model.AgentExecutionRequest");

        for (String removedType : removedTypes) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(removedType));
        }
    }

    @Test
    void shouldKeepApplicationRunAsFourRootFacts() {
        assertEquals(4, ApplicationRun.class.getRecordComponents().length);
        assertEquals(List.of("executionId", "request", "conversation", "budget"),
                java.util.Arrays.stream(ApplicationRun.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList());
    }

    @Test
    void shouldBuildAgentScopeContextOnlyInsideAgentRuntime() {
        assertThrows(NoSuchFieldException.class,
                () -> AgentRunRequest.class.getDeclaredField("runtimeContext"));
        for (Field field : AgentExecutionAdapter.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().startsWith("io.agentscope."),
                    () -> "Application Agent适配器不得持有AgentScope运行对象：" + field.getName());
        }
    }

    @Test
    void shouldNotExposeLegacyAgentSelectorInChatRequest() {
        assertThrows(NoSuchFieldException.class, () -> ChatRequest.class.getDeclaredField("agentCode"));
        assertThrows(NoSuchMethodException.class, () -> ChatRequest.class.getMethod("getAgentCode"));
    }

    @Test
    void shouldExposeFrozenSkillCatalogWithoutApplicationActivationDecision() throws Exception {
        Method load = SkillRuntimeService.class.getMethod("load",
                com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition.class,
                com.lingxi.workbench.workflow.model.ContextBudgetPolicy.class, List.class);

        assertEquals(SkillRuntimeCatalog.class, load.getReturnType());
        assertThrows(NoSuchMethodException.class,
                () -> SkillRuntimeService.class.getMethod("resolveAlwaysOn",
                        com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition.class,
                        com.lingxi.workbench.workflow.model.ContextBudgetPolicy.class));
        assertEquals(4, AgentScopeCheckpoint.CURRENT_VERSION);
    }
}
