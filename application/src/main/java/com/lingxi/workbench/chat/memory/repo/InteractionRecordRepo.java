package com.lingxi.workbench.chat.memory.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.chat.memory.model.entity.InteractionRecordEntity;
import com.lingxi.workbench.chat.memory.repo.mapper.InteractionRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/** 交互记录数据仓库。 */
@Repository
@RequiredArgsConstructor
public class InteractionRecordRepo {
    private final InteractionRecordMapper mapper;

    /** 插入一条交互记录，返回主键。 */
    public Long insert(Long userId, Long conversationId, Long assistantMessageId, String summary, Map<String, Object> entities) {
        InteractionRecordEntity entity = new InteractionRecordEntity();
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setAssistantMessageId(assistantMessageId);
        entity.setSummary(summary);
        entity.setEntities(entities);
        mapper.insert(entity);
        return entity.getId();
    }

    /** 按 recordId 查询。 */
    public InteractionRecordEntity findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按 Assistant 消息幂等键查询既有记录主键。 */
    public Optional<Long> findIdByAssistantMessageId(Long assistantMessageId) {
        return Optional.ofNullable(mapper.selectOne(
                        Wrappers.<InteractionRecordEntity>lambdaQuery()
                                .select(InteractionRecordEntity::getId)
                                .eq(InteractionRecordEntity::getAssistantMessageId, assistantMessageId)))
                .map(InteractionRecordEntity::getId);
    }

    /** 按 userId 分页查询最近的交互记录。 */
    public java.util.List<InteractionRecordEntity> findRecentByUserId(Long userId, int limit) {
        return mapper.selectList(
                Wrappers.<InteractionRecordEntity>lambdaQuery()
                        .eq(InteractionRecordEntity::getUserId, userId)
                        .orderByDesc(InteractionRecordEntity::getCreatedAt)
                        .last("LIMIT " + limit));
    }
}
