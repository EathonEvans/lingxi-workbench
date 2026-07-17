package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** Prompt历史版本响应。 */
public record PromptVersionVO(
        /** 版本主键。 */
        String id,
        /** 版本号。 */
        Integer versionNo,
        /** Prompt正文内容。 */
        String promptContent,
        /** 变量声明列表。 */
        List<PromptVariableVO> variables,
        /** 版本状态：DRAFT / PUBLISHED。 */
        PromptVersionStatus status,
        /** 版本变更说明。 */
        String changeNote,
        /** 发布时间。 */
        OffsetDateTime publishedAt,
        /** 创建人用户主键。 */
        String createdBy,
        /** 创建时间。 */
        OffsetDateTime createdAt,
        /** 更新时间。 */
        OffsetDateTime updatedAt) {}
