package com.lingxi.workbench.admin.appmonitor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用管理端 Langfuse 只读接口配置。 */
@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
public class LangfuseConfiguration {
}
