package com.lingxi.workbench.chat.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.request.ConversationPageQuery;
import com.lingxi.workbench.chat.repo.mapper.ConversationMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 会话数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ConversationRepo {

    private final ConversationMapper mapper;

    /** 分页查询当前用户未删除的会话。 */
    public PageResult<ConversationEntity> findPage(Long userId, ConversationPageQuery query) {
        IPage<ConversationEntity> page = mapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<ConversationEntity>lambdaQuery()
                        .eq(ConversationEntity::getUserId, userId)
                        .isNull(ConversationEntity::getDeletedAt)
                        .eq(query.getStatus() != null, ConversationEntity::getStatus, query.getStatus())
                        .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
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

    /** 锁定用户会话，供消息创建事务使用。 */
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
}
