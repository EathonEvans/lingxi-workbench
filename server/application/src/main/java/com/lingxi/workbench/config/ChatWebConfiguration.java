package com.lingxi.workbench.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Chat SSE 的有界异步执行配置，避免生产环境使用默认的无界临时线程。
 */
@Configuration
public class ChatWebConfiguration implements WebMvcConfigurer {

    static final String EXECUTOR_NAME = "chatMvcTaskExecutor";
    /** Servlet 异步超时设为 0，SSE 生命周期只由终态、取消或连接断开控制。 */
    static final long NO_ASYNC_TIMEOUT_MILLIS = 0L;

    private final ThreadPoolTaskExecutor executor = createExecutor();

    /**
     * 为 Spring MVC 异步请求提供独立、有界且可传播观测上下文的线程池。
     */
    @Bean(name = EXECUTOR_NAME)
    public AsyncTaskExecutor chatMvcTaskExecutor() {
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(NO_ASYNC_TIMEOUT_MILLIS);
    }

    private static ThreadPoolTaskExecutor createExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lingxi-chat-mvc-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(256);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
