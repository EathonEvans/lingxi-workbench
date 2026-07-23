package com.lingxi.workbench.framework.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Date;

/**
 * 基于阿里云 OSS 的对象存储实现，适用于生产环境。
 */
@RequiredArgsConstructor
public class AliOssObjectStorage implements ObjectStorage {

    private final OSS client;

    private final String bucket;

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(content.length);
            PutObjectRequest request = new PutObjectRequest(
                    bucket, objectKey, new ByteArrayInputStream(content), metadata);
            client.putObject(request);
        } catch (Exception e) {
            throw new IllegalStateException("OSS对象上传失败: " + objectKey, e);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            OSSObject object = client.getObject(bucket, objectKey);
            return object.getObjectContent();
        } catch (Exception e) {
            throw new IllegalStateException("OSS对象读取失败: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(bucket, objectKey);
        } catch (Exception e) {
            throw new IllegalStateException("OSS对象删除失败: " + objectKey, e);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration expires) {
        return presignedGetUrl(objectKey, expires, null);
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration expires, String responseContentType) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expires.toMillis());
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey);
            request.setExpiration(expiration);
            if (responseContentType != null && !responseContentType.isBlank()) {
                ResponseHeaderOverrides responseHeaders = new ResponseHeaderOverrides();
                responseHeaders.setContentType(responseContentType);
                request.setResponseHeaders(responseHeaders);
            }
            return client.generatePresignedUrl(request).toString();
        } catch (Exception e) {
            throw new IllegalStateException("OSS临时访问URL生成失败: " + objectKey, e);
        }
    }
}
