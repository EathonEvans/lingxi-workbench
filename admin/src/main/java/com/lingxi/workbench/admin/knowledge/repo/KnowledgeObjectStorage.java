package com.lingxi.workbench.admin.knowledge.repo;

import com.lingxi.workbench.framework.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.io.InputStream;

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

    /** 删除原始文档。 */
    public void delete(String objectKey) {
        objectStorage.delete(objectKey);
    }
}
