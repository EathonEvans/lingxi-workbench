package com.lingxi.workbench.framework.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 所有启动模块共用的 JSON 序列化配置。
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer nonNullPropertyCustomizer() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
