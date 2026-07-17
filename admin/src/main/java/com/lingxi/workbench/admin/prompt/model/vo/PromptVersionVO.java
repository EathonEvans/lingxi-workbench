package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** Prompt 历史版本响应。 */
public record PromptVersionVO(
        String id,
        Integer versionNo,
        String promptContent,
        List<PromptVariableVO> variables,
        PromptVersionStatus status,
        String changeNote,
        OffsetDateTime publishedAt,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
