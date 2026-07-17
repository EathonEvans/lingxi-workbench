package com.lingxi.workbench.admin.prompt.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Prompt 主记录数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_prompt")
public class PromptEntity extends AuditTimeEntity {
    private Long id;
    private String name;
    private String code;
    private String description;
    private EnableStatus status;
    private Long publishedVersionId;
    private Long createdBy;
    private Long updatedBy;
}
