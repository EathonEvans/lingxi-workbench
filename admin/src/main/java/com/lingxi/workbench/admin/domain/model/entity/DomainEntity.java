package com.lingxi.workbench.admin.domain.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 业务领域树节点。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_domain")
public class DomainEntity extends TimeEntity {
    private Long id;
    private Long parentId;
    private Long rootId;
    private String name;
    private String code;
    private String description;
    private Integer depth;
    private Integer sortOrder;
    private EnableStatus status;
    private Long createdBy;
    private Long updatedBy;
}
