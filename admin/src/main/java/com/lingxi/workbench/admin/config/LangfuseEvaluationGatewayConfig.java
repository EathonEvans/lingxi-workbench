package com.lingxi.workbench.admin.config;

import com.lingxi.workbench.admin.appmonitor.config.LangfuseProperties;
import com.lingxi.workbench.admin.evaluation.gateway.LangfuseEvaluationGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangfuseEvaluationGatewayConfig {

    @Bean
    public LangfuseEvaluationGateway langfuseEvaluationGateway(LangfuseProperties langfuseProperties) {
        return new LangfuseEvaluationGateway(langfuseProperties);
    }
}
