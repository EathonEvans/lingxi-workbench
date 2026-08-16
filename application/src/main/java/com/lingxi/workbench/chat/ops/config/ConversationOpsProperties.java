package com.lingxi.workbench.chat.ops.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 会话事件对账与内部运维接口配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.chat.ops")
public class ConversationOpsProperties {

    private boolean enabled = true;
    private Duration staleAfter = Duration.ofMinutes(10);
    private Duration memoryDelay = Duration.ofMinutes(2);
    private Duration reconcileDelay = Duration.ofMinutes(1);
    private int batchSize = 100;
    private String token;
}
