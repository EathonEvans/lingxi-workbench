package com.lingxi.workbench.admin.knowledge.repo;

import com.lingxi.workbench.framework.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.time.Duration;

/**
 * 知识库文档原始文件存储仓库，委托给当前启用的统一对象存储实现。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeObjectStorage {

    private final ObjectStorage objectStorage;

    /** 保存原始文档，解析任务始终从对象存储读取同一份输入。 */
    public void put(String objectKey, byte[] content, String contentType) {
        objectStorage.put(objectKey, content, contentType);
    }

    /** 读取原始文档内容，调用方负责关闭流。 */
    public InputStream get(String objectKey) {
        return objectStorage.get(objectKey);
    }

    /** 生成原始文档的临时预览地址，避免公开对象存储桶。 */
    public String presignedGetUrl(String objectKey, Duration expires) {
        return objectStorage.presignedGetUrl(objectKey, expires);
    }

    /** 生成指定响应类型的临时预览地址，用于覆盖历史对象不准确的响应元数据。 */
    public String presignedGetUrl(String objectKey, Duration expires, String responseContentType) {
        return objectStorage.presignedGetUrl(objectKey, expires, responseContentType);
    }

    /** 删除原始文档。 */
    public void delete(String objectKey) {
        objectStorage.delete(objectKey);
    }
}
