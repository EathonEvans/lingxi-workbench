package com.lingxi.workbench.application.runtime.service.capability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册 Tool 对账的有限频率配置；任务调度由统一会话运维配置开启。 */
@Configuration
@EnableConfigurationProperties(CapabilityReconciliationProperties.class)
public class CapabilityReconcileConfig {
}
