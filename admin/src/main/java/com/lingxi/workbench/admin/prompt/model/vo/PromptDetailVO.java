package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** Prompt 编辑详情响应。 */
public record PromptDetailVO(
        String id,
        String name,
        String code,
        String description,
        EnableStatus status,
        String publishedVersionId,
        Integer publishedVersionNo,
        String draftVersionId,
        Integer draftVersionNo,
        String promptContent,
        List<PromptVariableVO> variables,
        String changeNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
