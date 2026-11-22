package com.lingxi.workbench.framework.storage.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import com.lingxi.workbench.framework.storage.impl.AliOssObjectStorage;
import com.lingxi.workbench.framework.storage.impl.MinioObjectStorage;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 统一对象存储自动配置，根据 {@code lingxi.storage.type} 创建对应的存储实现 Bean。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
@ConditionalOnProperty(prefix = "lingxi.storage", name = "type")
public class ObjectStorageAutoConfiguration {


    /**
     * 阿里云 OSS 存储 Bean，当配置为 {@code lingxi.storage.type=oss} 时生效。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "lingxi.storage", name = "type", havingValue = "oss")
    public ObjectStorage aliOssObjectStorage(ObjectStorageProperties properties) {
        ObjectStorageProperties.Oss oss = properties.oss();
        requireText(oss.endpoint(), "lingxi.storage.oss.endpoint");
        requireText(oss.accessKeyId(), "lingxi.storage.oss.access-key-id");
        requireText(oss.accessKeySecret(), "lingxi.storage.oss.access-key-secret");
        requireText(oss.bucket(), "lingxi.storage.oss.bucket");
        OSS ossClient = new OSSClientBuilder().build(
                oss.endpoint().trim(),
                oss.accessKeyId().trim(),
                oss.accessKeySecret().trim());
        return new AliOssObjectStorage(ossClient, properties.oss().bucket());
    }

    /**
     * MinIO 存储 Bean，当配置为 {@code lingxi.storage.type=minio} 时生效。
     */
    @Bean
    @ConditionalOnClass(MinioClient.class)
    @ConditionalOnProperty(prefix = "lingxi.storage", name = "type", havingValue = "minio")
    public ObjectStorage minioObjectStorage(ObjectStorageProperties properties) {
        ObjectStorageProperties.Minio minio = properties.minio();
        requireText(minio.endpoint(), "lingxi.storage.minio.endpoint");
        requireText(minio.accessKey(), "lingxi.storage.minio.access-key");
        requireText(minio.secretKey(), "lingxi.storage.minio.secret-key");
        requireText(minio.bucket(), "lingxi.storage.minio.bucket");
        log.info("启用 MinIO 对象存储: endpoint={}, bucket={}", minio.endpoint(), minio.bucket());
        MinioClient client = MinioClient.builder()
                .endpoint(minio.endpoint().trim())
                .credentials(minio.accessKey().trim(), minio.secretKey().trim())
                .build();
        return new MinioObjectStorage(client, minio.bucket());
    }

    /** 校验对象存储必填配置，启动阶段直接暴露错误配置。 */
    private static void requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + "不能为空");
        }
    }
}
