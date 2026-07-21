package com.lingxi.workbench.admin.knowledge.repo;

import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** MinIO原始文档存储仓库。 */
@Repository
@RequiredArgsConstructor
public class KnowledgeObjectStorage {
    private final MinioClient minioClient;
    private final KnowledgeProperties properties;

    /** 保存原始文档，解析任务始终从对象存储读取同一份输入。 */
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.storage().bucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("原始文档保存失败", exception);
        }
    }

    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.storage().bucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("原始文档读取失败", exception);
        }
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.storage().bucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("原始文档删除失败", exception);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        String bucket = properties.storage().bucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
