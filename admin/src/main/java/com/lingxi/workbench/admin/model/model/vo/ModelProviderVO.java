package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.time.OffsetDateTime;

/** 模型供应商响应，主键使用字符串避免 JavaScript 大整数精度丢失。 */
public record ModelProviderVO(
        /** 供应商主键，使用字符串避免 JavaScript 大整数精度丢失。 */
        String id,
        /** 供应商名称。 */
        String name,
        /** 供应商编码。 */
        String code,
        /** 协议类型。 */
        String protocol,
        /** 接口基础地址。 */
        String baseUrl,
        /** 启用状态。 */
        EnableStatus status,
        /** 备注说明。 */
        String remark,
        /** 更新时间。 */
        OffsetDateTime updatedAt) {}
