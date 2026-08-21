package com.lingxi.workbench.application.runtime;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.application.runtime.service.agent.AgentChatServiceImpl;
import com.lingxi.workbench.application.runtime.service.app.ApplicationChatExecutionServiceImpl;
import com.lingxi.workbench.application.runtime.service.capability.CapabilityExecutionServiceImpl;
import com.lingxi.workbench.application.runtime.service.plan.SupervisorAgentExecutor;
import com.lingxi.workbench.application.runtime.service.skill.SkillRuntimeService;
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
                ApplicationChatExecutionServiceImpl.class,
                CapabilityExecutionServiceImpl.class,
                AgentChatServiceImpl.class,
                SupervisorAgentExecutor.class);

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
                "com.lingxi.workbench.application.runtime.service.plan.PlannerRuntimeCoordinator",
                "com.lingxi.workbench.application.runtime.service.plan.PlanExecutionService",
                "com.lingxi.workbench.application.runtime.service.plan.FinalSynthesisService");

        for (String removedType : removedTypes) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(removedType));
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
        assertEquals(3, RuntimeCheckpoint.CURRENT_VERSION);
    }
}
