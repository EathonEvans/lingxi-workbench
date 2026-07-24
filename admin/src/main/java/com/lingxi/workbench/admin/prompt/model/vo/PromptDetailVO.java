package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Prompt编辑详情响应。
 *
 * @param id                 Prompt主键
 * @param name               Prompt名称
 * @param code               Prompt编码
 * @param role               Prompt角色
 * @param description        Prompt描述说明
 * @param status             启用状态
 * @param publishedVersionId 已发布版本主键
 * @param publishedVersionNo 已发布版本号
 * @param draftVersionId     草稿版本主键
 * @param draftVersionNo     草稿版本号
 * @param promptContent      草稿Prompt正文内容
 * @param variables          草稿变量声明列表
 * @param changeNote         草稿版本变更说明
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PromptDetailVO {

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
     * 草稿版本主键。
     */
    private String draftVersionId;

    /**
     * 草稿版本号。
     */
    private Integer draftVersionNo;

    /**
     * 草稿Prompt正文内容。
     */
    private String promptContent;

    /**
     * 草稿变量声明列表。
     */
    private List<PromptVariableVO> variables;

    /**
     * 草稿版本变更说明。
     */
    private String changeNote;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间。
     */
    private OffsetDateTime updatedAt;
}
