package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptRole;

import java.time.OffsetDateTime;
import java.util.List;

/** Prompt编辑详情响应。 */
public record PromptDetailVO(
        /** Prompt主键。 */
        String id,
        /** Prompt名称。 */
        String name,
        /** Prompt编码。 */
        String code,
        /** Prompt角色。 */
        PromptRole role,
        /** Prompt描述说明。 */
        String description,
        /** 启用状态。 */
        EnableStatus status,
        /** 已发布版本主键。 */
        String publishedVersionId,
        /** 已发布版本号。 */
        Integer publishedVersionNo,
        /** 草稿版本主键。 */
        String draftVersionId,
        /** 草稿版本号。 */
        Integer draftVersionNo,
        /** 草稿Prompt正文内容。 */
        String promptContent,
        /** 草稿变量声明列表。 */
        List<PromptVariableVO> variables,
        /** 草稿版本变更说明。 */
        String changeNote,
        /** 创建时间。 */
        OffsetDateTime createdAt,
        /** 更新时间。 */
        OffsetDateTime updatedAt) {}
