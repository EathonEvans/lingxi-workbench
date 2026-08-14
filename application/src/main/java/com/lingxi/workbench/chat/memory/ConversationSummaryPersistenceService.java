package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.memory.model.entity.ConversationSummarySnapshotEntity;
import com.lingxi.workbench.chat.memory.repo.ConversationSummarySnapshotRepo;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 在短事务中追加摘要快照并推进会话当前摘要投影。 */
@Service
@RequiredArgsConstructor
public class ConversationSummaryPersistenceService {

    private final ConversationRepo conversationRepo;
    private final ConversationSummarySnapshotRepo snapshotRepo;

    /**
     * 保存仍基于当前摘要生成的结果；并发产生的旧结果不会覆盖较新的摘要。
     *
     * @return true 表示新快照生效，false 表示结果已经过期
     */
    @Transactional
    public boolean isSavedIfCurrent(AssistantCompletedEvent event, ConversationSummarySource source, String summary,
                                    MemoryConfig memoryConfig) {
        ConversationSummaryMemoryConfig config = memoryConfig.summary();
        ConversationEntity conversation = conversationRepo.lockOwnedById(event.turn().conversationId(), event.userId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
        if (!Objects.equals(conversation.getSummarySequenceNo(), source.previousCoveredUserSequenceNo())) {
            return false;
        }

        ConversationSummarySnapshotEntity snapshot = new ConversationSummarySnapshotEntity();
        snapshot.setConversationId(event.turn().conversationId());
        snapshot.setUserId(event.userId());
        snapshot.setApplicationVersionId(event.applicationVersionId());
        snapshot.setCoveredUserSequenceNo(source.coveredUserSequenceNo());
        snapshot.setSummary(summary);
        snapshot.setPromptVersionId(config.getPromptVersionId());
        snapshot.setModelInstanceId(memoryConfig.getChatModelInstanceId());
        snapshotRepo.insert(snapshot);

        boolean promoted = conversationRepo.isSummaryPromoted(event.turn().conversationId(),
                source.previousCoveredUserSequenceNo(), source.coveredUserSequenceNo(), summary);
        if (!promoted) {
            throw new IllegalStateException("会话摘要投影推进失败");
        }
        return true;
    }
}
