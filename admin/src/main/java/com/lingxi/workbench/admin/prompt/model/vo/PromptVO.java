package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptRole;

import java.time.OffsetDateTime;

/** Prompt列表响应。 */
public record PromptVO(
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
        /** 草稿版本号。 */
        Integer draftVersionNo,
        /** 创建时间。 */
        OffsetDateTime createdAt,
        /** 更新时间。 */
        OffsetDateTime updatedAt) {}
