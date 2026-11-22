package com.lingxi.workbench.admin.application.model.vo;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Application 当前可编辑草稿响应。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationDraftVO {

    private final String id;
    private final String baseVersionId;
    private final Integer schemaVersion;
    private final ApplicationDefinition definition;
    private final Long revision;
    private final String changeNote;
}
