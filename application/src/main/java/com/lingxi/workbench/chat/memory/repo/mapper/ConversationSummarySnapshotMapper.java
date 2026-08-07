package com.lingxi.workbench.chat.memory.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.chat.memory.model.entity.ConversationSummarySnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/** 会话摘要快照 Mapper。 */
@Mapper
public interface ConversationSummarySnapshotMapper extends BaseMapper<ConversationSummarySnapshotEntity> {
}
