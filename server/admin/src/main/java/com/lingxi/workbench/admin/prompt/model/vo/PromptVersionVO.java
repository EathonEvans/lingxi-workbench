package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Prompt历史版本响应。
 *
 * @param id            版本主键
 * @param versionNo     版本号
 * @param promptContent Prompt正文内容
 * @param variables     变量声明列表
 * @param status        版本状态：DRAFT / PUBLISHED
 * @param changeNote    版本变更说明
 * @param publishedAt   发布时间
 * @param createdBy     创建人用户主键
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PromptVersionVO {

    /**
     * 版本主键。
     */
    private String id;

    /**
     * 版本号。
     */
    private Integer versionNo;

    /**
     * Prompt正文内容。
     */
    private String promptContent;

    /**
     * 变量声明列表。
     */
    private List<PromptVariableVO> variables;

    /**
     * 版本状态：DRAFT / PUBLISHED。
     */
    private PromptVersionStatus status;

    /**
     * 版本变更说明。
     */
    private String changeNote;

    /**
     * 发布时间。
     */
    private OffsetDateTime publishedAt;

    /**
     * 创建人用户主键。
     */
    private String createdBy;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间。
     */
    private OffsetDateTime updatedAt;
}
