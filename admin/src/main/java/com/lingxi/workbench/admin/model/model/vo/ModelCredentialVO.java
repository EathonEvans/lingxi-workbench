package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;

import java.time.OffsetDateTime;

/** 模型密钥脱敏响应，所有关联主键均使用字符串传输。 */
public record ModelCredentialVO(
        /** 密钥主键。 */
        String id,
        /** 所属供应商主键。 */
        String providerId,
        /** 密钥名称。 */
        String name,
        /** 密钥脱敏提示。 */
        String secretHint,
        /** 密钥状态。 */
        ModelCredentialStatus status,
        /** 最近一次轮换时间。 */
        OffsetDateTime rotatedAt,
        /** 更新时间。 */
        OffsetDateTime updatedAt) {}
