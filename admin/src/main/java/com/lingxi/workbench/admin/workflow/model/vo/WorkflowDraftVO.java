package com.lingxi.workbench.admin.workflow.model.vo;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.admin.workflow.model.config.WorkflowCanvasState;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Workflow 草稿 VO。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowDraftVO {
    private final String id;
    private final String baseVersionId;
    private final Integer schemaVersion;
    private final WorkflowDefinition definition;
    private final WorkflowCanvasState canvasState;
    private final Long revision;
    private final String changeNote;
}
