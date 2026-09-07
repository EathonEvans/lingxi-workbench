package com.lingxi.workbench.infra.ai.outbound.config;

import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型出站地址安全策略装配。
 *
 * <p>模型与 Embedding 链路属于 infra-ai 的核心职责，策略在此自注册，
 * 依赖 infra-ai 的可运行服务（admin/application）自动获得统一校验。</p>
 */
@Configuration
@EnableConfigurationProperties(ModelOutboundSecurityProperties.class)
public class ModelOutboundSecurityConfig {

    @Bean
    public ModelOutboundAddressPolicy modelOutboundAddressPolicy(ModelOutboundSecurityProperties properties) {
        return new ModelOutboundAddressPolicy(properties);
    }
}
