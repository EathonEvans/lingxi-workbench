package com.lingxi.workbench.observability.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/** Agent 观测事件异步落库配置。 */
@Slf4j
@Configuration
public class AiObservationAsyncConfiguration {

    public static final String EXECUTOR_NAME = "aiObservationTaskExecutor";

    /**
     * 单线程保证同一 Trace 的开始、阶段和终态按提交顺序落库；有界队列避免观测故障拖垮主链路。
     */
    @Bean(name = EXECUTOR_NAME)
    public Executor aiObservationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lingxi-observation-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        AtomicLong rejectedTasks = new AtomicLong();
        executor.setRejectedExecutionHandler((task, pool) -> {
            long rejected = rejectedTasks.incrementAndGet();
            if (rejected == 1 || rejected % 100 == 0) {
                log.warn("Agent观测事件队列已满，已丢弃事件，累计丢弃={}", rejected);
            }
        });
        executor.initialize();
        return executor;
    }
}
