package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Prompt列表响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PromptVO {

    /**
     * Prompt主键。
     */
    private String id;

    /**
     * Prompt名称。
     */
    private String name;

    /**
     * Prompt编码。
     */
    private String code;

    /**
     * Prompt角色。
     */
    private PromptRole role;

    /**
     * Prompt描述说明。
     */
    private String description;

    /**
     * 启用状态。
     */
    private EnableStatus status;

    /**
     * 已发布版本主键。
     */
    private String publishedVersionId;

    /**
     * 已发布版本号。
     */
    private Integer publishedVersionNo;

    /**
     * 草稿版本号。
     */
    private Integer draftVersionNo;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间。
     */
    private OffsetDateTime updatedAt;
}
