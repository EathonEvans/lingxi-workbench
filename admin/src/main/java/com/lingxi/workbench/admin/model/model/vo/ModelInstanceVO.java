package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 模型实例响应，所有主键和关联主键均使用字符串传输。 */
public record ModelInstanceVO(
        /** 实例主键。 */
        String id,
        /** 所属供应商主键。 */
        String providerId,
        /** 关联密钥主键。 */
        String credentialId,
        /** 实例名称。 */
        String name,
        /** 实例编码。 */
        String code,
        /** 供应商模型标识。 */
        String modelCode,
        /** 模型类型。 */
        String modelType,
        /** 模型支持的可选能力列表。 */
        List<String> capabilities,
        /** 温度参数。 */
        BigDecimal temperature,
        /** 核采样参数。 */
        BigDecimal topP,
        /** 单次生成最大 Token 数。 */
        Integer maxTokens,
        /** 模型上下文窗口大小。 */
        Integer contextWindow,
        /** 停止生成序列列表。 */
        List<String> stopSequences,
        /** 请求超时时间（秒）。 */
        Integer timeoutSeconds,
        /** 最大重试次数。 */
        Integer maxRetries,
        /** 是否为默认实例。 */
        Boolean isDefault,
        /** 启用状态。 */
        EnableStatus status,
        /** 最近一次连通性测试结果。 */
        String lastTestStatus,
        /** 最近一次测试延迟（毫秒）。 */
        Integer lastTestLatencyMs,
        /** 最近一次测试错误码。 */
        String lastTestErrorCode,
        /** 最近一次测试消息。 */
        String lastTestMessage,
        /** 最近一次测试时间。 */
        OffsetDateTime lastTestAt,
        /** 更新时间。 */
        OffsetDateTime updatedAt) {}
