package com.lingxi.workbench.framework.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一对象存储配置属性。
 *
 * <p>通过 {@code lingxi.storage.type} 切换存储实现：{@code minio} 或 {@code oss}。</p>
 */
@ConfigurationProperties(prefix = "lingxi.storage")
public record ObjectStorageProperties(
        Minio minio,
        Oss oss) {

    public record Minio(
            String endpoint,
            String accessKey,
            String secretKey,
            String bucket) {
    }

    public record Oss(
            String endpoint,
            String accessKeyId,
            String accessKeySecret,
            String bucket) {
    }
}
