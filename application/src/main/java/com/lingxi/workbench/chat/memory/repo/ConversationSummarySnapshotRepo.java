package com.lingxi.workbench.chat.memory.repo;

import com.lingxi.workbench.chat.memory.model.entity.ConversationSummarySnapshotEntity;
import com.lingxi.workbench.chat.memory.repo.mapper.ConversationSummarySnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 会话摘要快照的追加式持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ConversationSummarySnapshotRepo {

    private final ConversationSummarySnapshotMapper mapper;

    public void insert(ConversationSummarySnapshotEntity snapshot) {
        mapper.insert(snapshot);
    }
}
