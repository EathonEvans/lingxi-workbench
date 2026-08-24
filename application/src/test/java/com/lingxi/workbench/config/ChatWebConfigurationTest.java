package com.lingxi.workbench.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatWebConfigurationTest {

    @Test
    void shouldConfigureBoundedExecutorForMvcAsyncRequests() {
        ChatWebConfiguration configuration = new ChatWebConfiguration();
        AsyncSupportConfigurer configurer = mock(AsyncSupportConfigurer.class);
        AsyncTaskExecutor executor = configuration.chatMvcTaskExecutor();

        configuration.configureAsyncSupport(configurer);

        verify(configurer).setTaskExecutor(executor);
        verify(configurer).setDefaultTimeout(ChatWebConfiguration.NO_ASYNC_TIMEOUT_MILLIS);
    }
}
