package com.lingxi.workbench.agentexecute.config;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 工具调用执行器装配测试。 */
class ToolCallingConfigTest {

    @Test
    void shouldRegisterToolCallingManagerWithAppObservationRegistry() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObservationRegistry.class, ObservationRegistry::create);
            context.register(ToolCallingConfig.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(ToolCallingManager.class).size());
        }
    }
}
