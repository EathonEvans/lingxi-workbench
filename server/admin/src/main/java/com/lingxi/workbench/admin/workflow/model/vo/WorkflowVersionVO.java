package com.lingxi.workbench.admin.workflow.model.vo;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.admin.workflow.model.config.WorkflowCanvasState;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Workflow 发布版本 VO。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowVersionVO {
    private final String id;
    private final String workflowId;
    private final Integer versionNo;
    private final String versionName;
    private final Integer schemaVersion;
    private final WorkflowDefinition definitionSnapshot;
    private final WorkflowCanvasState canvasSnapshot;
    private final String snapshotChecksum;
    private final String changeNote;
    private final OffsetDateTime publishedAt;
}
