package com.lingxi.workbench.framework.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一对象存储配置属性。
 *
 * <p>通过 {@code lingxi.storage.type} 切换存储实现：{@code minio} 或 {@code oss}。</p>
 *
 * @param type  存储类型标识，可选值：{@code minio}、{@code oss}
 * @param minio MinIO 存储配置
 * @param oss   阿里云 OSS 存储配置
 */
@ConfigurationProperties(prefix = "lingxi.storage")
public record ObjectStorageProperties(
        String type,
        Minio minio,
        Oss oss) {

    /**
     * MinIO 对象存储连接配置。
     *
     * @param endpoint  服务端点地址
     * @param accessKey 访问密钥
     * @param secretKey 访问私钥
     * @param bucket    存储桶名称
     */
    public record Minio(
            String endpoint,
            String accessKey,
            String secretKey,
            String bucket) {
    }

    /**
     * 阿里云 OSS 对象存储连接配置。
     *
     * @param endpoint        服务端点地址
     * @param accessKeyId     访问密钥 ID
     * @param accessKeySecret 访问密钥 Secret
     * @param bucket          存储桶名称
     */
    public record Oss(
            String endpoint,
            String accessKeyId,
            String accessKeySecret,
            String bucket) {
    }
}
