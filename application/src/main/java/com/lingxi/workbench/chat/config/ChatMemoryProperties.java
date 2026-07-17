package com.lingxi.workbench.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 会话窗口记忆配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "lingxi.chat.memory")
public class ChatMemoryProperties {

    /** 最多携带的完整问答轮数。 */
    private int maxRounds = 10;

    /** 历史消息允许占用的最大估算 Token 数。 */
    private int maxTokens = 12000;
}
