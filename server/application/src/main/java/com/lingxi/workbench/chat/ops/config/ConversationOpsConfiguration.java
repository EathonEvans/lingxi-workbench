package com.lingxi.workbench.chat.ops.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启会话事件运维配置与有限频率对账任务。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ConversationOpsProperties.class)
public class ConversationOpsConfiguration {
}
