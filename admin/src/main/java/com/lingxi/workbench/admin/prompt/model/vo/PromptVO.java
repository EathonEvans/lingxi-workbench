package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.time.OffsetDateTime;

/** Prompt 列表响应。 */
public record PromptVO(
        String id,
        String name,
        String code,
        String description,
        EnableStatus status,
        String publishedVersionId,
        Integer publishedVersionNo,
        Integer draftVersionNo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
