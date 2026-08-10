package com.lingxi.workbench.admin.common;

import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 管理端 AI 观测基础设施装配测试。 */
class AdminAiObservationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class))
            .withBean(OpenAiCompatibleChatModelFactory.class);

    @Test
    void shouldProvideObservationRegistryForInfraAiComponents() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObservationRegistry.class);
            assertThat(context).hasSingleBean(OpenAiCompatibleChatModelFactory.class);
        });
    }
}
