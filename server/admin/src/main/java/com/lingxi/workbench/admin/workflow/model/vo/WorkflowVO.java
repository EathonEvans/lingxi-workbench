package com.lingxi.workbench.admin.workflow.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Workflow 列表项 VO。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowVO {
    private final String id;
    private final String name;
    private final String code;
    private final String description;
    private final String status;
    private final String publishedVersionId;
    private final OffsetDateTime updatedAt;
}
