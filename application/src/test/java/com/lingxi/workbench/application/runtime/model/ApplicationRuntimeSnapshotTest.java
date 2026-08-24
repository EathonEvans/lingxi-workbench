package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class ApplicationRuntimeSnapshotTest {

    private static final ApplicationDefinition VALID_DEFINITION = definition(null);

    @Test
    void shouldRejectBlankApplicationCode() {
        assertThrows(IllegalArgumentException.class, () -> new AppRuntimeSnapshot(1L, " ", 2L, VALID_DEFINITION));
    }

    @Test
    void shouldRejectMissingPublishedVersion() {
        assertThrows(NullPointerException.class, () -> new AppRuntimeSnapshot(1L, "support", null, VALID_DEFINITION));
    }

    @Test
    void shouldRejectDefinitionWithoutAnyExecutionEntry() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AppRuntimeSnapshot(1L, "support", 2L,
                        ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION).build()));

        assertEquals("Application缺少fallback Agent能力编码", error.getMessage());
    }

    @Test
    void shouldRejectLegacySchema() {
        ApplicationDefinition definition = definition(null);
        definition.setSchemaVersion(APPLICATION_SCHEMA_VERSION - 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AppRuntimeSnapshot(1L, "support", 2L, definition));

        assertEquals("Application运行定义版本不受支持", error.getMessage());
    }

    @Test
    void shouldAllowIntentRoutingThatReferencesLockedCapability() {
        ApplicationDefinition definition = definition(ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .capabilityCode("refund_workflow")
                .targetId(20L)
                .targetVersionId(21L)
                .build());

        assertDoesNotThrow(() -> new AppRuntimeSnapshot(1L, "support", 2L, definition));
    }

    @Test
    void shouldRejectIntentRoutingOutsideCapabilityCatalog() {
        ApplicationDefinition definition = definition(ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .capabilityCode("unknown")
                .targetId(20L)
                .targetVersionId(21L)
                .build());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AppRuntimeSnapshot(1L, "support", 2L, definition));

        assertEquals("Application意图路由未引用锁定能力目录", error.getMessage());
    }

    @Test
    void shouldRejectIntentRoutingWithoutCapabilityCode() {
        ApplicationDefinition definition = definition(ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .targetId(20L)
                .targetVersionId(21L)
                .build());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AppRuntimeSnapshot(1L, "support", 2L, definition));

        assertEquals("Application意图路由能力编码不能为空", error.getMessage());
    }

    private static ApplicationDefinition definition(ApplicationIntentMapping mapping) {
        ApplicationCapabilityDefinition fallbackCap = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(10L)
                .targetVersionId(11L)
                .build();
        ApplicationCapabilityDefinition workflowCapability = ApplicationCapabilityDefinition.builder()
                .code("refund_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(20L)
                .targetVersionId(21L)
                .build();
        ApplicationIntentBinding intentRouting = ApplicationIntentBinding.builder()
                .enabled(mapping != null)
                .intentTreeId(30L)
                .intentTreeVersionId(31L)
                .mappings(mapping == null ? List.of() : List.of(mapping))
                .build();
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(fallbackCap, workflowCapability))
                        .build())
                .intentRouting(mapping == null ? null : intentRouting)
                .build();
    }
}
