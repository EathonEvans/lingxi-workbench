package com.lingxi.workbench.admin.workflow.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/** Workflow 详情 VO，含草稿和版本列表。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowDetailVO {
    private final String id;
    private final String name;
    private final String code;
    private final String description;
    private final String status;
    private final WorkflowDraftVO draft;
    private final String publishedVersionId;
    private final List<WorkflowVersionVO> versions;
    private final OffsetDateTime updatedAt;
}
