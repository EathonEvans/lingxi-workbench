package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;

import java.time.OffsetDateTime;

/** 模型密钥脱敏响应，所有关联主键均使用字符串传输。 */
public record ModelCredentialVO(
        String id,
        String providerId,
        String name,
        String secretHint,
        ModelCredentialStatus status,
        OffsetDateTime rotatedAt,
        OffsetDateTime updatedAt) {}
