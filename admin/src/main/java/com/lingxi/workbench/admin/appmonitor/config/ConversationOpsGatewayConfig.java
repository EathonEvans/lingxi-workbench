package com.lingxi.workbench.admin.appmonitor.config;

import com.lingxi.workbench.admin.appmonitor.gateway.ConversationOpsGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/** 启用管理端到Application会话运维接口的受控代理配置。 */
@Configuration
@EnableConfigurationProperties(ConversationOpsGatewayProperties.class)
public class ConversationOpsGatewayConfig {

    @Bean("conversationOpsHttpClient")
    public HttpClient conversationOpsHttpClient(ConversationOpsGatewayProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    @Bean
    public ConversationOpsGateway conversationOpsGateway(
            ConversationOpsGatewayProperties properties,
            @Qualifier("conversationOpsHttpClient") HttpClient httpClient) {
        return new ConversationOpsGateway(properties, httpClient);
    }
}
