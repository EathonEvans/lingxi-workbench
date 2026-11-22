package com.lingxi.workbench.admin.intent.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 意图树发布版本摘要。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentTreeVersionVO {

    private final String id;
    private final Integer versionNo;
    private final String versionName;
    private final Integer schemaVersion;
    private final String embeddingModelInstanceId;
    private final String snapshotChecksum;
    private final String changeNote;
    private final String publishedBy;
    private final OffsetDateTime publishedAt;
}
