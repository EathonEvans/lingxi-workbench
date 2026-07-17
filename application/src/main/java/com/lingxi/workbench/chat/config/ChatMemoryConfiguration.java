package com.lingxi.workbench.chat.config;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 会话记忆依赖配置。 */
@Configuration
public class ChatMemoryConfiguration {

    /**
     * 提供与大语言模型 Token 规则接近的本地估算器。
     * 该结果用于限制上下文窗口，不作为计费数据。
     */
    @Bean
    public TokenCountEstimator tokenCountEstimator() {
        return new JTokkitTokenCountEstimator();
    }
}
