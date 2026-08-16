package com.lingxi.workbench.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/** Chat SSE 的有界异步执行配置，避免生产环境使用默认的无界临时线程。 */
@Configuration
public class ChatWebConfiguration implements WebMvcConfigurer {

    static final String EXECUTOR_NAME = "chatMvcTaskExecutor";
    /** 传输层兜底必须覆盖Application最大执行时限及流式宽限，不能提前终止后台任务。 */
    static final long ASYNC_TIMEOUT_MILLIS = Duration.ofHours(2).toMillis();

    private final ThreadPoolTaskExecutor executor = createExecutor();

    /** 为 Spring MVC 异步请求提供独立、有界且可传播观测上下文的线程池。 */
    @Bean(name = EXECUTOR_NAME)
    public AsyncTaskExecutor chatMvcTaskExecutor() {
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(ASYNC_TIMEOUT_MILLIS);
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
