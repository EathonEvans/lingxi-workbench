package com.lingxi.workbench.admin.intent.model.vo;

import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 意图树不可变发布版本详情。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentTreeVersionDetailVO {

    private final String id;
    private final String intentTreeId;
    private final Integer versionNo;
    private final String versionName;
    private final Integer schemaVersion;
    private final String embeddingModelInstanceId;
    private final IntentTreeDefinition definition;
    private final String snapshotChecksum;
    private final String changeNote;
    private final String publishedBy;
    private final OffsetDateTime publishedAt;
}
