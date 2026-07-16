package com.lingxi.workbench.admin.model.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.Data;
import java.time.OffsetDateTime;

/** 模型供应商数据库实体。 */
@Data
@TableName("lingxi_core.ai_model_provider")
public class ModelProviderEntity {
    private Long id;
    private String name;
    private String code;
    private String protocol;
    private String baseUrl;
    private EnableStatus status;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
