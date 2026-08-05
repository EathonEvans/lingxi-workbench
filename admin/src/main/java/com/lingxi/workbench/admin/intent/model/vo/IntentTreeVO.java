package com.lingxi.workbench.admin.intent.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 意图树列表响应。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentTreeVO {

    private final String id;
    private final String name;
    private final String code;
    private final String description;
    private final EnableStatus status;
    private final String publishedVersionId;
    private final Integer publishedVersionNo;
    private final Long draftRevision;
    private final OffsetDateTime updatedAt;
}
