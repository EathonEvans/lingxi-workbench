package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.time.OffsetDateTime;

/** 模型供应商响应，主键使用字符串避免 JavaScript 大整数精度丢失。 */
public record ModelProviderVO(
        String id,
        String name,
        String code,
        String protocol,
        String baseUrl,
        EnableStatus status,
        String remark,
        OffsetDateTime updatedAt) {}
