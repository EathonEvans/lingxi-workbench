package com.lingxi.workbench.admin.application.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Application 发布版本摘要响应。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationVersionVO {

    private final String id;
    private final Integer versionNo;
    private final String versionName;
    private final Integer schemaVersion;
    private final String snapshotChecksum;
    private final String changeNote;
    private final String publishedBy;
    private final OffsetDateTime publishedAt;
}
