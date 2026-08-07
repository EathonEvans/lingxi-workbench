package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationRuntimeSnapshotTest {

    private static final ApplicationDefinition VALID_DEFINITION =
            new ApplicationDefinition(
                    1,
                    new WorkflowDefinition(
                            1,
                            "main",
                            List.of(),
                            List.of()));

    @Test
    void shouldRejectBlankApplicationCode() {
        assertThrows(IllegalArgumentException.class, () -> new ApplicationRuntimeSnapshot(1L, " ", 2L, VALID_DEFINITION));
    }

    @Test
    void shouldRejectMissingPublishedVersion() {
        assertThrows(NullPointerException.class, () -> new ApplicationRuntimeSnapshot(1L, "support", null, VALID_DEFINITION));
    }

    @Test
    void shouldRejectDefinitionWithoutAnyExecutionEntry() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ApplicationRuntimeSnapshot(1L, "support", 2L, new ApplicationDefinition(1, null)));

        assertEquals("Application缺少默认Agent、意图路由或历史Workflow", error.getMessage());
    }

    @Test
    void shouldAllowIntentRoutingWithoutEmbeddedWorkflow() {
        ApplicationIntentBinding intentBinding = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(10L)
                .intentTreeVersionId(11L)
                .mappings(List.of())
                .build();
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(2)
                .intentBinding(intentBinding)
                .build();

        assertDoesNotThrow(() -> new ApplicationRuntimeSnapshot(1L, "support", 2L, definition));
    }

    @Test
    void shouldAllowV5FastRouteThatReferencesLockedCapability() {
        ApplicationDefinition definition = v5Definition(ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .capabilityCode("refund_workflow")
                .targetId(20L)
                .targetVersionId(21L)
                .build());

        assertDoesNotThrow(() -> new ApplicationRuntimeSnapshot(1L, "support", 2L, definition));
    }

    @Test
    void shouldRejectV5FastRouteOutsideCapabilityCatalog() {
        ApplicationDefinition definition = v5Definition(ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .capabilityCode("unknown")
                .targetId(20L)
                .targetVersionId(21L)
                .build());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ApplicationRuntimeSnapshot(1L, "support", 2L, definition));

        assertEquals("Application快速路由未引用锁定能力目录", error.getMessage());
    }

    @Test
    void shouldRejectV5FastRouteWithoutCapabilityCode() {
        ApplicationDefinition definition = v5Definition(ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .targetId(20L)
                .targetVersionId(21L)
                .build());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ApplicationRuntimeSnapshot(1L, "support", 2L, definition));

        assertEquals("Application快速路由能力编码不能为空", error.getMessage());
    }

    private static ApplicationDefinition v5Definition(ApplicationIntentMapping mapping) {
        ApplicationAgentReference defaultAgent = new ApplicationAgentReference(10L, 11L, "默认助手", "处理普通请求");
        ApplicationCapabilityDefinition defaultCapability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(10L)
                .targetVersionId(11L)
                .build();
        ApplicationCapabilityDefinition workflowCapability = ApplicationCapabilityDefinition.builder()
                .code("refund_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(20L)
                .targetVersionId(21L)
                .build();
        ApplicationIntentBinding fastRoutes = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(30L)
                .intentTreeVersionId(31L)
                .mappings(List.of(mapping))
                .build();
        return ApplicationDefinition.builder()
                .schemaVersion(5)
                .defaultAgent(defaultAgent)
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(defaultCapability, workflowCapability))
                        .build())
                .optionalFastRoutes(fastRoutes)
                .build();
    }
}
