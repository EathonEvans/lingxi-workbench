package com.lingxi.workbench.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 模型供应商基本字段，应用端只读。 */
@Data
@TableName("lingxi_core.ai_model_provider")
public class ModelProviderView {
    private Long id;
    private String baseUrl;
    private String status;
}
