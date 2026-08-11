package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.model.bo.InteractionMemoryWrite;
import com.lingxi.workbench.chat.memory.repo.InteractionRecordRepo;
import com.lingxi.workbench.chat.memory.repo.InteractionRecordVectorRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 在一个短事务中原子保存交互记录及其向量。
 */
@Service
@RequiredArgsConstructor
public class InteractionMemoryStoreService {

    private final InteractionRecordRepo recordRepo;
    private final InteractionRecordVectorRepo vectorRepo;

    @Transactional(readOnly = true)
    public Optional<Long> findExistingRecordId(Long assistantMessageId) {
        return recordRepo.findIdByAssistantMessageId(assistantMessageId);
    }

    @Transactional
    public Long save(InteractionMemoryWrite write) {
        Long recordId = recordRepo.insert(write.getUserId(), write.getConversationId(), write.getAssistantMessageId(), write.getSummary(), write.getEntities());
        vectorRepo.insert(recordId, write.getEmbeddingModelInstanceId(), write.getEmbedding());
        return recordId;
    }
}
