package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 模型实例响应，所有主键和关联主键均使用字符串传输。 */
public record ModelInstanceVO(
        String id,
        String providerId,
        String credentialId,
        String name,
        String code,
        String modelCode,
        String modelType,
        List<String> capabilities,
        BigDecimal temperature,
        BigDecimal topP,
        Integer maxTokens,
        Integer contextWindow,
        List<String> stopSequences,
        Integer timeoutSeconds,
        Integer maxRetries,
        Boolean isDefault,
        EnableStatus status,
        String lastTestStatus,
        OffsetDateTime updatedAt) {}
