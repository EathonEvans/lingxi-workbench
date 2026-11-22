package com.lingxi.workbench.admin.intent.model.vo;

import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 意图树草稿响应。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentTreeDraftVO {

    private final String id;
    private final String baseVersionId;
    private final Integer schemaVersion;
    private final String embeddingModelInstanceId;
    private final IntentTreeDefinition definition;
    private final Long revision;
    private final String changeNote;
}
