package com.lingxi.workbench.admin.model.model.vo;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 模型实例响应，所有主键和关联主键均使用字符串传输。
 *
 * @param id               实例主键
 * @param providerId       所属供应商主键
 * @param credentialId     关联密钥主键
 * @param name             实例名称
 * @param code             实例编码
 * @param modelCode        供应商模型标识
 * @param modelType        模型类型
 * @param capabilities     模型支持的可选能力列表
 * @param temperature      温度参数
 * @param topP             核采样参数
 * @param maxTokens        单次生成最大 Token 数
 * @param contextWindow    模型上下文窗口大小
 * @param stopSequences    停止生成序列列表
 * @param extensionParameters 供应商特有的 OpenAI Chat 请求体扩展参数
 * @param timeoutSeconds   请求超时时间（秒）
 * @param maxRetries       最大重试次数
 * @param isDefault        是否为默认实例
 * @param status           启用状态
 * @param lastTestStatus   最近一次连通性测试结果
 * @param lastTestLatencyMs 最近一次测试延迟（毫秒）
 * @param lastTestErrorCode 最近一次测试错误码
 * @param lastTestMessage  最近一次测试消息
 * @param lastTestAt       最近一次测试时间
 * @param updatedAt        更新时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelInstanceVO {

    private String id;

    private String providerId;

    private String credentialId;

    private String name;

    private String code;

    private String modelCode;

    private String modelType;

    private List<String> capabilities;

    private BigDecimal temperature;

    private BigDecimal topP;

    private Integer maxTokens;

    private Integer contextWindow;

    private List<String> stopSequences;

    private JSONObject extensionParameters;

    private Integer timeoutSeconds;

    private Integer maxRetries;

    private Boolean isDefault;

    private EnableStatus status;

    private String lastTestStatus;

    private Integer lastTestLatencyMs;

    private String lastTestErrorCode;

    private String lastTestMessage;

    private OffsetDateTime lastTestAt;

    private OffsetDateTime updatedAt;
}
