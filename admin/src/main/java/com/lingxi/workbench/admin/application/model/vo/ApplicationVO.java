package com.lingxi.workbench.admin.application.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Application 列表响应。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationVO {

    private final String id;
    private final String name;
    private final String code;
    private final String description;
    private final EnableStatus status;
    private final String publishedVersionId;
    private final Integer publishedVersionNo;
    private final boolean publishedVersionReady;
    private final Long draftRevision;
    private final OffsetDateTime updatedAt;
}
