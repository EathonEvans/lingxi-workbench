package com.lingxi.workbench.admin.model.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.repo.typehandler.JsonbStringTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 可被业务稳定引用的模型实例。 */
@Data
@TableName(value = "lingxi_core.ai_model_instance", autoResultMap = true)
public class ModelInstanceEntity {
    private Long id;
    private Long providerId;
    private Long credentialId;
    private String name;
    private String code;
    private String modelCode;
    private ModelType modelType;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String capabilities;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private Integer contextWindow;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String stopSequences;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private Boolean isDefault;
    private EnableStatus status;
    private String lastTestStatus;
    private Integer lastTestLatencyMs;
    private String lastTestErrorCode;
    private String lastTestMessage;
    private OffsetDateTime lastTestAt;
    private Long createdBy;
    private Long updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
