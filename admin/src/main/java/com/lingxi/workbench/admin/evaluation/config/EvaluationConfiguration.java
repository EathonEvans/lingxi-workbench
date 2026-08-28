package com.lingxi.workbench.admin.evaluation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 评测任务使用独立有界线程池，避免长时间模型调用占用管理接口线程。 */
@Configuration
public class EvaluationConfiguration {

    @Bean("evaluationTaskExecutor")
    public Executor evaluationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("evaluation-");
        executor.initialize();
        return executor;
    }
}
