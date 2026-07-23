package com.lingxi.workbench.admin.knowledge.schedule;

import com.lingxi.workbench.admin.common.persistence.KnowledgeMqTopics;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.model.message.KnowledgeChunkTaskMessage;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkTaskRepo;
import com.lingxi.workbench.framework.mq.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 补偿事务提交后因进程退出等原因未成功投递的任务消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeChunkTaskRecovery {
    private final KnowledgeChunkTaskRepo taskRepo;
    private final MqProducer<Object> mqProducer;

    @Scheduled(fixedDelayString = "${lingxi.knowledge.chunk.recovery-interval-millis:30000}")
    public void recover() {
        taskRepo.resetStale(OffsetDateTime.now().minusMinutes(30));
        for (Long taskId : taskRepo.findIdsByStatus(KnowledgeChunkTaskStatus.PENDING)) {
            sendChunkTask(taskId);
        }
    }

    private void sendChunkTask(Long taskId) {
        try {
            mqProducer.send(KnowledgeMqTopics.CHUNK_EXECUTE, String.valueOf(taskId),
                    new KnowledgeChunkTaskMessage(taskId));
        } catch (Exception exception) {
            log.warn("分块补偿消息发送失败，等待下一轮重试，taskId={}", taskId, exception);
        }
    }

}
