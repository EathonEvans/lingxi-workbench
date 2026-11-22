package com.lingxi.workbench.framework.storage.impl;

import com.lingxi.workbench.framework.storage.ObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 的对象存储实现，适用于开发环境或自建对象存储场景。
 */
@RequiredArgsConstructor
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;


    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储上传失败: " + objectKey, e);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储读取失败: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储删除失败: " + objectKey, e);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration expires) {
        return presignedGetUrl(objectKey, expires, null);
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration expires, String responseContentType) {
        try {
            GetPresignedObjectUrlArgs.Builder builder = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) expires.getSeconds(), TimeUnit.SECONDS);
            if (responseContentType != null && !responseContentType.isBlank()) {
                builder.extraQueryParams(Map.of("response-content-type", responseContentType));
            }
            return client.getPresignedObjectUrl(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO临时访问URL生成失败: " + objectKey, e);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
