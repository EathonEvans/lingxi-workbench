package com.lingxi.workbench.agentexecute.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Agent工具调用执行器装配。 */
@Configuration(proxyBeanMethods = false)
public class ToolCallingConfig {

    /**
     * 使用应用级观测注册表创建默认工具调用管理器，使工具执行进入当前AI链路。
     *
     * @param observationRegistry 应用级观测注册表
     * @return Spring AI工具调用管理器
     */
    @Bean
    @ConditionalOnMissingBean(ToolCallingManager.class)
    public ToolCallingManager toolCallingManager(ObservationRegistry observationRegistry) {
        return ToolCallingManager.builder()
                .observationRegistry(observationRegistry)
                .build();
    }
}
