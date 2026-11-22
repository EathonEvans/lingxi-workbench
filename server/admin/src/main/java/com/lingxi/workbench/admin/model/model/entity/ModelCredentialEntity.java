package com.lingxi.workbench.admin.model.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.OffsetDateTime;

/**
 * 模型访问密钥数据库实体，密文和 IV 禁止对外返回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_model_credential")
public class ModelCredentialEntity extends TimeEntity {
    /** 密钥主键。 */
    private Long id;
    /** 所属供应商主键。 */
    private Long providerId;
    /** 密钥名称，同一供应商下唯一。 */
    private String name;
    /**
     * 撤销密钥时必须把密文真正更新为 NULL，不能使用 MyBatis-Plus 默认非空策略。
     */
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String secretCiphertext;

    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String secretIv;
    /** 密钥脱敏提示，用于前端展示。 */
    private String secretHint;
    /** 密钥状态：ACTIVE / DISABLED / REVOKED。 */
    private ModelCredentialStatus status;
    /** 最近一次轮换时间。 */
    private OffsetDateTime rotatedAt;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}
