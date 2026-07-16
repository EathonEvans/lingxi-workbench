package com.lingxi.workbench.admin.model.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import lombok.Data;
import lombok.ToString;
import java.time.OffsetDateTime;

/** 模型访问密钥数据库实体，密文和 IV 禁止对外返回。 */
@Data
@TableName("lingxi_core.ai_model_credential")
public class ModelCredentialEntity {
    private Long id;
    private Long providerId;
    private String name;
    /** 撤销密钥时必须把密文真正更新为 NULL，不能使用 MyBatis-Plus 默认非空策略。 */
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String secretCiphertext;

    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String secretIv;
    private String secretHint;
    private ModelCredentialStatus status;
    private OffsetDateTime rotatedAt;
    private Long createdBy;
    private Long updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
