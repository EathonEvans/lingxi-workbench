package com.lingxi.workbench.chat.config;

import com.lingxi.workbench.infra.ai.token.TextTokenEstimator;
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

    /**
     * 将 Spring AI 的 Token 估算能力适配为 Agent Runtime 可依赖的稳定契约。
     * 该 Bean 仅由 Application 装配，避免 infra-ai 的实现被其他服务无条件扫描。
     */
    @Bean
    public TextTokenEstimator textTokenEstimator(TokenCountEstimator tokenCountEstimator) {
        return tokenCountEstimator::estimate;
    }

}
