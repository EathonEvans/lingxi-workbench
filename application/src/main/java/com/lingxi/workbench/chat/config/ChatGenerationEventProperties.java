package com.lingxi.workbench.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话生成事件的 Redis 短期缓存配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "lingxi.chat.generation-event")
public class ChatGenerationEventProperties {

    /** 单个生成任务最多保留的文本和工具状态事件数。 */
    private int maxEvents = 2000;

    /** 生成事件的保留时间，用于浏览器刷新、网络恢复和跨进程终态续接。 */
    private int ttlHours = 24;
}
