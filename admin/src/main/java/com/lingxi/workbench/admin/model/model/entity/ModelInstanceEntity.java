package com.lingxi.workbench.admin.model.model.entity;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 可被业务稳定引用的模型实例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_model_instance", autoResultMap = true)
public class ModelInstanceEntity extends TimeEntity {
    /** 实例主键。 */
    private Long id;
    /** 所属供应商主键。 */
    private Long providerId;
    /** 关联密钥主键。 */
    private Long credentialId;
    /** 实例名称。 */
    private String name;
    /** 实例编码，全局唯一。 */
    private String code;
    /** 供应商模型标识，如 DeepSeek/chat-v3。 */
    private String modelCode;
    /** 模型类型：CHAT / EMBEDDING / RERANK。 */
    private ModelType modelType;
    /** 模型支持的可选能力列表。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<ModelCapability> capabilities;
    /** 温度参数，控制生成随机性，范围 0~2。 */
    private BigDecimal temperature;
    /** 核采样参数，控制生成多样性，范围 0~1。 */
    private BigDecimal topP;
    /** 单次生成最大 Token 数。 */
    private Integer maxTokens;
    /** 模型上下文窗口大小。 */
    private Integer contextWindow;
    /** 停止生成序列列表。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> stopSequences;
    /** 供应商特有的 OpenAI Chat 请求体扩展参数。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject extensionParameters;
    /** 请求超时时间（秒）。 */
    private Integer timeoutSeconds;
    /** 最大重试次数，范围 0~2。 */
    private Integer maxRetries;
    /** 是否为该模型类型的默认实例。 */
    private Boolean isDefault;
    /** 启用状态：ACTIVE / DISABLED。 */
    private EnableStatus status;
    /** 最近一次连通性测试结果。 */
    private ModelConnectionTestStatus lastTestStatus;
    /** 最近一次测试延迟（毫秒）。 */
    private Integer lastTestLatencyMs;
    /** 最近一次测试错误码。 */
    private String lastTestErrorCode;
    /** 最近一次测试消息。 */
    private String lastTestMessage;
    /** 最近一次测试时间。 */
    private OffsetDateTime lastTestAt;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}
