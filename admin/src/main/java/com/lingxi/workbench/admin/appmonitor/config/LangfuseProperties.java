package com.lingxi.workbench.admin.appmonitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Langfuse Public API 配置，密钥只能通过环境变量注入。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.langfuse")
public class LangfuseProperties {
    private boolean apiEnabled;
    private String baseUrl;
    private String publicKey;
    private String secretKey;
    private Duration timeout = Duration.ofSeconds(5);
}
