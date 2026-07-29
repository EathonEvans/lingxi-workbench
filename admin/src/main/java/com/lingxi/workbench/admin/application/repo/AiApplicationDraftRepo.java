package com.lingxi.workbench.admin.application.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationDraftEntity;
import com.lingxi.workbench.admin.application.repo.mapper.AiApplicationDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Application 草稿数据仓库。 */
@Repository
@RequiredArgsConstructor
public class AiApplicationDraftRepo {
    private final AiApplicationDraftMapper mapper;

    /** 按 Application ID 查询草稿。 */
    public Optional<AiApplicationDraftEntity> findByApplicationId(Long applicationId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AiApplicationDraftEntity>lambdaQuery()
                        .eq(AiApplicationDraftEntity::getApplicationId, applicationId)));
    }

    /** 带行锁按 Application ID 查询草稿，用于 saveDraft 等写操作。 */
    public Optional<AiApplicationDraftEntity> findByApplicationIdForUpdate(Long applicationId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AiApplicationDraftEntity>lambdaQuery()
                        .eq(AiApplicationDraftEntity::getApplicationId, applicationId)
                        .last("FOR UPDATE")));
    }

    /** 批量按 Application ID 列表查询草稿，用于列表页填充 revision。 */
    public List<AiApplicationDraftEntity> findAllByApplicationIds(List<Long> applicationIds) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<AiApplicationDraftEntity>lambdaQuery()
                .in(AiApplicationDraftEntity::getApplicationId, applicationIds));
    }

    public void insert(AiApplicationDraftEntity entity) {
        mapper.insert(entity);
    }

    /** 以 revision 乐观锁更新草稿字段，避免并发覆盖。 */
    public boolean updateWithRevision(AiApplicationDraftEntity entity, Long expectedRevision) {
        return mapper.updateWithRevision(entity, expectedRevision) == 1;
    }

    /** 发布后同步更新草稿基线版本号和 revision。 */
    public void updatePublicationBase(
            Long draftId, Long baseVersionId, Long operatorId, Long expectedRevision) {
        int affected = mapper.update(
                null,
                Wrappers.<AiApplicationDraftEntity>lambdaUpdate()
                        .set(AiApplicationDraftEntity::getBaseVersionId, baseVersionId)
                        .set(AiApplicationDraftEntity::getUpdatedBy, operatorId)
                        .set(AiApplicationDraftEntity::getRevision, expectedRevision + 1)
                        .set(AiApplicationDraftEntity::getUpdatedAt, OffsetDateTime.now())
                        .eq(AiApplicationDraftEntity::getId, draftId)
                        .eq(AiApplicationDraftEntity::getRevision, expectedRevision));
        if (affected != 1) {
            throw new IllegalStateException("Application草稿发布基线更新失败");
        }
    }
}
