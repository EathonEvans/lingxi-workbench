package com.lingxi.workbench.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 模型实例基本字段，应用端只读。 */
@Data
@TableName("lingxi_core.ai_model_instance")
public class ModelInstanceView {
    private Long id;
    private Long providerId;
    private Long credentialId;
    private String modelType;
    private String modelCode;
    private String status;
    private int timeoutSeconds;
    private int maxRetries;
}
