package com.lingxi.workbench.admin.workflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Workflow 稳定身份数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_workflow")
public class WorkflowEntity extends TimeEntity {
    private Long id;
    private String name;
    private String code;
    private String description;
    private EnableStatus status;
    private Long publishedVersionId;
    private Long createdBy;
    private Long updatedBy;
}
