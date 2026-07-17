package com.lingxi.workbench.admin.prompt.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.persistence.JsonbStringTypeHandler;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** Prompt 版本数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_prompt_version", autoResultMap = true)
public class PromptVersionEntity extends AuditTimeEntity {
    private Long id;
    private Long promptId;
    private Integer versionNo;
    private String promptContent;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String variables;
    private PromptVersionStatus status;
    private String changeNote;
    private OffsetDateTime publishedAt;
    private Long createdBy;
    private Long updatedBy;
}
