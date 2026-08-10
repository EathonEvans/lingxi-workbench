package com.lingxi.workbench.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME;

/** 会话记忆依赖配置。 */
@EnableAsync
@Configuration
@Slf4j
public class ChatMemoryConfiguration {

    /**
     * 提供与大语言模型 Token 规则接近的本地估算器。
     * 该结果用于限制上下文窗口，不作为计费数据。
     */
    @Bean
    public TokenCountEstimator tokenCountEstimator() {
        return new JTokkitTokenCountEstimator();
    }

    /**
     * 异步记忆后处理使用有界执行器，并传播 Micrometer Observation 上下文。
     * 队列满时记录丢弃数量，禁止拒绝异常反向破坏已完成的聊天主链路。
     */
    @Bean(name = APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public Executor applicationTaskExecutor() {
        TaskDecorator decorator = new ContextPropagatingTaskDecorator();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lingxi-async-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setTaskDecorator(decorator);
        AtomicLong rejectedTasks = new AtomicLong();
        executor.setRejectedExecutionHandler((task, pool) -> {
            long rejected = rejectedTasks.incrementAndGet();
            if (rejected == 1 || rejected % 100 == 0) {
                log.warn("异步记忆任务队列已满，已丢弃任务，累计丢弃={}", rejected);
            }
        });
        executor.initialize();
        return executor;
    }
}
