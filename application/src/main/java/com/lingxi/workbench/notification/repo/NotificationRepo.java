package com.lingxi.workbench.notification.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.notification.model.entity.NotificationEntity;
import com.lingxi.workbench.notification.repo.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** 站内通知数据访问语义封装。 */
@Repository
@RequiredArgsConstructor
public class NotificationRepo {

    private final NotificationMapper mapper;

    /** 按创建时间倒序分页查询本人未删除通知。 */
    public PageResult<NotificationEntity> findPage(Long userId, PageQuery query) {
        IPage<NotificationEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<NotificationEntity>lambdaQuery()
                        .eq(NotificationEntity::getUserId, userId)
                        .isNull(NotificationEntity::getDeletedAt)
                        .orderByDesc(NotificationEntity::getCreatedAt)
                        .orderByDesc(NotificationEntity::getId));
        return PageResult.convert(page);
    }

    /** 统计本人未读通知数。 */
    public long countUnread(Long userId) {
        return mapper.selectCount(
                Wrappers.<NotificationEntity>lambdaQuery()
                        .eq(NotificationEntity::getUserId, userId)
                        .isNull(NotificationEntity::getReadAt)
                        .isNull(NotificationEntity::getDeletedAt));
    }

    /** 幂等写入带去重键的通知，返回 true 表示本次写入成功，false 表示去重命中既有记录。 */
    public boolean insertWithDedupIfAbsent(NotificationEntity entity) {
        return mapper.insertWithDedupIfAbsent(
                entity.getUserId(), entity.getType().getCode(), entity.getTitle(), entity.getContent(),
                entity.getPayload(), entity.getDedupKey()) > 0;
    }

    /** 按去重键将本人通知置为已读，返回实际更新的行数。 */
    public int markReadByDedupKey(Long userId, String dedupKey) {
        return mapper.markReadByDedupKey(userId, dedupKey, OffsetDateTime.now());
    }

    /** 批量将本人通知置为已读，返回实际更新的行数。 */
    public int markReadBatch(Long userId, List<Long> ids) {
        return mapper.markReadBatch(userId, ids, OffsetDateTime.now());
    }

    /** 将本人全部未读通知置为已读，返回实际更新的行数。 */
    public int markAllRead(Long userId) {
        return mapper.markAllRead(userId, OffsetDateTime.now());
    }
}
