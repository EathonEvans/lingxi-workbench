package com.lingxi.workbench.admin.knowledge.config;

import com.lingxi.workbench.framework.storage.config.ObjectStorageAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 知识库文档异步解析配置。
 *
 * <p>对象存储 Bean 由 framework 层
 * {@link ObjectStorageAutoConfiguration} 统一创建，
 * 通过 {@code lingxi.storage.type} 控制 MinIO 或 OSS 实现。</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeConfiguration {

    /** 创建文档解析专用线程池，避免解析任务阻塞主业务线程。 */
    @Bean(name = "knowledgeTaskExecutor")
    public Executor knowledgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("knowledge-parse-");
        executor.initialize();
        return executor;
    }
}
