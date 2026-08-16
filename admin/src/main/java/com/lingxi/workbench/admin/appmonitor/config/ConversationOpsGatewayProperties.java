package com.lingxi.workbench.admin.appmonitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Application会话运维内部接口配置，令牌只能通过部署环境注入。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.conversation-ops")
public class ConversationOpsGatewayProperties {
    private String baseUrl = "http://127.0.0.1:8080";
    private String token;
    private Duration timeout = Duration.ofSeconds(5);
}
