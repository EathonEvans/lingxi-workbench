package com.lingxi.workbench.admin.knowledge.event.listener;

import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeDeleteRequestedEvent;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeObjectStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 根据数据库事务结果补偿清理对象，维持文档记录与原始文件的一致性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeObjectListener {
    private final KnowledgeObjectStorage objectStorage;

    /**
     * 文档记录删除后清理对象存储中的原始文件。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(KnowledgeDeleteRequestedEvent event) {
        deleteQuietly(event.objectKey(), "知识库文档记录已删除，但原始对象清理失败");
    }

    /**
     * 上传后的数据库事务未提交时删除对象，避免产生没有业务记录的孤儿文件。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void rollback(KnowledgeObjectStoredEvent event) {
        deleteQuietly(event.objectKey(), "知识库文档事务已回滚，但原始对象补偿删除失败");
    }

    /**
     * 静默删除对象，失败仅记录日志不影响主流程。
     */
    private void deleteQuietly(String objectKey, String failureMessage) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("{}，objectKey={}", failureMessage, objectKey, exception);
        }
    }
}
