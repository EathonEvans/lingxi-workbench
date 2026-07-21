package com.lingxi.workbench.admin.knowledge.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 知识库文档存储和异步解析配置。 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeConfiguration {
    @Bean
    public MinioClient knowledgeMinioClient(KnowledgeProperties properties) {
        KnowledgeProperties.Storage storage = properties.storage();
        return MinioClient.builder().endpoint(storage.endpoint())
                .credentials(storage.accessKey(), storage.secretKey()).build();
    }

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
