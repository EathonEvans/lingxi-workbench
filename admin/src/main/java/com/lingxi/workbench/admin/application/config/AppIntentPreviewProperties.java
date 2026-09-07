package com.lingxi.workbench.admin.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Admin 调用 Application 意图草稿测试接口的内部配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.intent-preview")
public class AppIntentPreviewProperties {

    private String baseUrl = "http://127.0.0.1:8080";
    private String token;
    private Duration timeout = Duration.ofSeconds(30);
}
