package com.lingxi.workbench.chat.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.request.ConversationPageQuery;
import com.lingxi.workbench.chat.repo.mapper.ConversationMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** 会话数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ConversationRepo {

    private final ConversationMapper mapper;

    /** 分页查询当前用户未删除的会话。 */
    public PageResult<ConversationEntity> findPage(Long userId, ConversationPageQuery query) {
        IPage<ConversationEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<ConversationEntity>lambdaQuery()
                        .eq(ConversationEntity::getUserId, userId)
                        .isNull(ConversationEntity::getDeletedAt)
                        .eq(Objects.nonNull(query.getStatus()), ConversationEntity::getStatus, query.getStatus())
                        .like(CharSequenceUtil.isNotBlank(query.getKeyword()),
                                ConversationEntity::getTitle,
                                query.getKeyword() == null ? null : query.getKeyword().trim())
                        .orderByDesc(ConversationEntity::getLastMessageAt)
                        .orderByDesc(ConversationEntity::getId));
        return PageResult.convert(page);
    }

    /** 查询用户所属且未删除的会话。 */
    public Optional<ConversationEntity> findOwnedById(Long id, Long userId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ConversationEntity>lambdaQuery()
                        .eq(ConversationEntity::getId, id)
                        .eq(ConversationEntity::getUserId, userId)
                        .isNull(ConversationEntity::getDeletedAt)));
    }

    /**
     * 使用数据库行锁查询用户所属且未删除的会话。
     * 锁持续到调用方事务结束，用于串行化同一会话的消息序号分配和生成状态校验。
     */
    public Optional<ConversationEntity> lockOwnedById(Long id, Long userId) {
        return Optional.ofNullable(mapper.selectOwnedForUpdate(id, userId));
    }

    /** 新增或更新会话。 */
    public void save(ConversationEntity entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /** 仅当当前覆盖位置与调用方读取时一致，才推进会话摘要投影。 */
    public boolean isSummaryPromoted(Long conversationId, Integer expectedSequenceNo, Integer coveredSequenceNo, String summary) {
        ConversationEntity entity = new ConversationEntity();
        entity.setSummary(summary);
        entity.setSummaryAt(java.time.OffsetDateTime.now());
        entity.setSummarySequenceNo(coveredSequenceNo);
        return mapper.update(
                entity,
                Wrappers.<ConversationEntity>lambdaUpdate()
                        .eq(ConversationEntity::getId, conversationId)
                        .eq(Objects.nonNull(expectedSequenceNo), ConversationEntity::getSummarySequenceNo, expectedSequenceNo)
                        .isNull(Objects.isNull(expectedSequenceNo), ConversationEntity::getSummarySequenceNo)) == 1;
    }
}
