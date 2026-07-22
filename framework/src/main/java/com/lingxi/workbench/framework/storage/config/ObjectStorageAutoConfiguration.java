package com.lingxi.workbench.framework.storage.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.lingxi.workbench.framework.storage.impl.AliOssObjectStorage;
import com.lingxi.workbench.framework.storage.impl.MinioObjectStorage;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 统一对象存储自动配置，根据 {@code lingxi.storage.type} 创建对应的存储实现 Bean。
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
@ConditionalOnClass(OSS.class)
public class ObjectStorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageAutoConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(OSS.class)
    public OSS ossClient(ObjectStorageProperties properties) {
        ObjectStorageProperties.Oss aliProperties = properties.oss();
        if (!StringUtils.hasText(aliProperties.endpoint())) {
            throw new IllegalStateException("component.storage.oss.endpoint must not be blank.");
        }
        if (!StringUtils.hasText(aliProperties.accessKeyId())) {
            throw new IllegalStateException("component.storage.oss.access-key-id must not be blank.");
        }
        if (!StringUtils.hasText(aliProperties.accessKeyId())) {
            throw new IllegalStateException("component.storage.oss.access-key-secret must not be blank.");
        }
        if (!StringUtils.hasText(aliProperties.bucket())) {
            throw new IllegalStateException("component.storage.oss.bucket-name must not be blank.");
        }
        return new OSSClientBuilder().build(
                aliProperties.endpoint().trim(),
                aliProperties.accessKeyId().trim(),
                aliProperties.accessKeySecret().trim()
        );
    }


    @Bean
    @ConditionalOnClass(OSS.class)
    public AliOssObjectStorage aliOssObjectStorage(OSS ossClient, ObjectStorageProperties properties){
        return new AliOssObjectStorage(ossClient, properties.oss().bucket());
    }



    /**
     * MinIO 存储 Bean，当配置为 {@code lingxi.storage.type=minio} 时生效。
     */
    @Bean
    @ConditionalOnClass(MinioClient.class)
    public MinioObjectStorage minioObjectStorage(ObjectStorageProperties props) {
        ObjectStorageProperties.Minio minio = props.minio();
        log.info("启用 MinIO 对象存储: endpoint={}, bucket={}", minio.endpoint(), minio.bucket());
        MinioClient client = MinioClient.builder()
                .endpoint(minio.endpoint())
                .credentials(minio.accessKey(), minio.secretKey())
                .build();
        return new MinioObjectStorage(client, minio.bucket());
    }



}
