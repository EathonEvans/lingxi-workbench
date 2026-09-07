package com.lingxi.workbench.infra.ai.rerank.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 重排序 HTTP 内存边界，适用于在线检索和管理端连通性测试。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "lingxi.model.rerank-http")
public class RerankHttpProperties {
    @Min(1024) @Max(4194304)
    private int maxRequestBytes = 262144;
    @Min(1024) @Max(1048576)
    private int maxResponseBytes = 65536;
}
