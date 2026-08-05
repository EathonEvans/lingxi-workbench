package com.lingxi.workbench.admin.intent.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeDraftEntity;
import com.lingxi.workbench.admin.intent.repo.mapper.IntentTreeDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 意图树草稿数据仓库。 */
@Repository
@RequiredArgsConstructor
public class IntentTreeDraftRepo {

    private final IntentTreeDraftMapper mapper;

    public Optional<IntentTreeDraftEntity> findByIntentTreeId(Long intentTreeId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<IntentTreeDraftEntity>lambdaQuery()
                        .eq(IntentTreeDraftEntity::getIntentTreeId, intentTreeId)));
    }

    /** 带行锁查询草稿，用于保存和发布。 */
    public Optional<IntentTreeDraftEntity> findByIntentTreeIdForUpdate(Long intentTreeId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<IntentTreeDraftEntity>lambdaQuery()
                        .eq(IntentTreeDraftEntity::getIntentTreeId, intentTreeId)
                        .last("FOR UPDATE")));
    }

    public List<IntentTreeDraftEntity> findAllByIntentTreeIds(List<Long> intentTreeIds) {
        if (intentTreeIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<IntentTreeDraftEntity>lambdaQuery()
                .in(IntentTreeDraftEntity::getIntentTreeId, intentTreeIds));
    }

    public void insert(IntentTreeDraftEntity entity) {
        mapper.insert(entity);
    }

    public boolean updateWithRevision(IntentTreeDraftEntity entity, Long expectedRevision) {
        return mapper.updateWithRevision(entity, expectedRevision) == 1;
    }

    /** 发布成功后更新草稿基线版本和 revision。 */
    public void updatePublicationBase(Long draftId, Long baseVersionId, Long operatorId, Long expectedRevision) {
        int affected = mapper.update(
                null,
                Wrappers.<IntentTreeDraftEntity>lambdaUpdate()
                        .set(IntentTreeDraftEntity::getBaseVersionId, baseVersionId)
                        .set(IntentTreeDraftEntity::getUpdatedBy, operatorId)
                        .set(IntentTreeDraftEntity::getRevision, expectedRevision + 1)
                        .set(IntentTreeDraftEntity::getUpdatedAt, OffsetDateTime.now())
                        .eq(IntentTreeDraftEntity::getId, draftId)
                        .eq(IntentTreeDraftEntity::getRevision, expectedRevision));
        if (affected != 1) {
            throw new IllegalStateException("意图树草稿发布基线更新失败");
        }
    }
}
