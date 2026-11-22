package com.lingxi.workbench.admin.application.config;

import com.lingxi.workbench.admin.application.gateway.ApplicationIntentPreviewGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/** 配置 Admin 到 Application 只读意图测试接口的受控 HTTP 边界。 */
@Configuration
@EnableConfigurationProperties(AppIntentPreviewProperties.class)
public class AppIntentPreviewConfig {

    @Bean("applicationIntentPreviewHttpClient")
    public HttpClient applicationIntentPreviewHttpClient(AppIntentPreviewProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    @Bean
    public ApplicationIntentPreviewGateway applicationIntentPreviewGateway(
            AppIntentPreviewProperties properties,
            @Qualifier("applicationIntentPreviewHttpClient") HttpClient httpClient) {
        return new ApplicationIntentPreviewGateway(properties, httpClient);
    }
}
