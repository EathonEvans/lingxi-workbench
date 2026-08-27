package com.lingxi.workbench.admin.application.model.vo;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Application 发布版本详情响应。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationVersionDetailVO {

    private final String id;
    private final String applicationId;
    private final Integer versionNo;
    private final String versionName;
    private final Integer schemaVersion;
    private final String name;
    private final String avatarUrl;
    private final ApplicationDefinition definition;
    private final String snapshotChecksum;
    private final String changeNote;
    private final String publishedBy;
    private final OffsetDateTime publishedAt;
}
