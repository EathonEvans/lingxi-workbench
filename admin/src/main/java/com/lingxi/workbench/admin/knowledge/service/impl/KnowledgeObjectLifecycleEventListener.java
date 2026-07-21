package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeObjectDeleteRequestedEvent;
import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeObjectStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 根据数据库事务结果补偿清理对象，维持文档记录与原始文件的一致性。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeObjectLifecycleEventListener {
    private final KnowledgeObjectStorage objectStorage;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(KnowledgeObjectDeleteRequestedEvent event) {
        deleteQuietly(event.objectKey(), "知识库文档记录已删除，但原始对象清理失败");
    }

    /** 上传后的数据库事务未提交时删除对象，避免产生没有业务记录的孤儿文件。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void rollback(KnowledgeObjectStoredEvent event) {
        deleteQuietly(event.objectKey(), "知识库文档事务已回滚，但原始对象补偿删除失败");
    }

    private void deleteQuietly(String objectKey, String failureMessage) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException exception) {
            // 事务状态已经确定，清理失败不能改变接口结果；记录日志供后续巡检处理。
            log.warn("{}，objectKey={}", failureMessage, objectKey, exception);
        }
    }
}
