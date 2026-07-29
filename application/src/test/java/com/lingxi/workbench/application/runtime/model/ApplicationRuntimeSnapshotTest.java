package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApplicationRuntimeSnapshot(
                        1L,
                        " ",
                        2L,
                        VALID_DEFINITION));
    }

    @Test
    void shouldRejectMissingPublishedVersion() {
        assertThrows(
                NullPointerException.class,
                () -> new ApplicationRuntimeSnapshot(
                        1L,
                        "support",
                        null,
                        VALID_DEFINITION));
    }

    @Test
    void shouldRejectMissingWorkflowDefinition() {
        assertThrows(
                NullPointerException.class,
                () -> new ApplicationRuntimeSnapshot(
                        1L,
                        "support",
                        2L,
                        new ApplicationDefinition(1, null)));
    }
}
