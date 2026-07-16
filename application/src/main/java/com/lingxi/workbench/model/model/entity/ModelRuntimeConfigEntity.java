package com.lingxi.workbench.model.model.entity;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

/** 模型实例、供应商和访问密钥联合查询结果。 */
@Data
public class ModelRuntimeConfigEntity {
    private Long instanceId;
    private String protocol;
    private String baseUrl;
    @ToString.Exclude
    private String secretCiphertext;
    @ToString.Exclude
    private String secretIv;
    private String modelCode;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private String stopSequences;
    private Integer timeoutSeconds;
    private Integer maxRetries;
}
