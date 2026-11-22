package com.lingxi.workbench.admin.knowledge.event.listener;

import com.lingxi.workbench.admin.knowledge.model.message.KnowledgeChunkTaskMessage;
import com.lingxi.workbench.admin.common.persistence.KnowledgeMqTopics;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeChunkRequestedEvent;
import com.lingxi.workbench.framework.mq.MqProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在任务事务提交后投递RocketMQ消息。 */
@Component
@RequiredArgsConstructor
public class KnowledgeChunkMqEventListener {
    private final MqProducer<Object> mqProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(KnowledgeChunkRequestedEvent event) {
        mqProducer.send(KnowledgeMqTopics.CHUNK_EXECUTE,
                String.valueOf(event.taskId()), new KnowledgeChunkTaskMessage(event.taskId()));
    }
}
