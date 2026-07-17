package com.lingxi.workbench.model.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import lombok.ToString;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.util.List;

/** 模型实例、供应商和访问密钥联合查询结果。 */
@Data
@TableName(value = "lingxi_core.ai_model_instance", autoResultMap = true)
public class ModelRuntimeConfigEntity {
    /** 模型实例主键。 */
    private Long instanceId;
    /** 协议类型。 */
    private String protocol;
    /** 接口基础地址。 */
    private String baseUrl;
    /** AES-256-GCM 加密后的密钥密文。 */
    @ToString.Exclude
    private String secretCiphertext;
    /** 密钥初始化向量。 */
    @ToString.Exclude
    private String secretIv;
    /** 供应商模型标识。 */
    private String modelCode;
    /** 温度参数。 */
    private BigDecimal temperature;
    /** 核采样参数。 */
    private BigDecimal topP;
    /** 单次生成最大 Token 数。 */
    private Integer maxTokens;
    /** 停止生成序列列表。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> stopSequences;
    /** 请求超时时间（秒）。 */
    private Integer timeoutSeconds;
    /** 最大重试次数。 */
    private Integer maxRetries;
}
