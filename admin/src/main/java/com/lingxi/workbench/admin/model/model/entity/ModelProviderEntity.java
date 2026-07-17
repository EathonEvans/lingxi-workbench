package com.lingxi.workbench.admin.model.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 模型供应商数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_model_provider")
public class ModelProviderEntity extends AuditTimeEntity {
    private Long id;
    private String name;
    private String code;
    private String protocol;
    private String baseUrl;
    private EnableStatus status;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
}
