package com.lingxi.workbench.admin.application.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Application 编辑详情响应。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationDetailVO {

    private final String id;
    private final String name;
    private final String code;
    private final String description;
    private final EnableStatus status;
    private final String publishedVersionId;
    private final Integer publishedVersionNo;
    private final boolean publishedVersionReady;
    private final ApplicationDraftVO draft;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
