package com.lingxi.workbench.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 模型密钥基本字段，应用端只读。 */
@Data
@TableName("lingxi_core.ai_model_credential")
public class ModelCredentialView {
    private Long id;
    private String secretCiphertext;
    private String secretIv;
    private String status;
}
