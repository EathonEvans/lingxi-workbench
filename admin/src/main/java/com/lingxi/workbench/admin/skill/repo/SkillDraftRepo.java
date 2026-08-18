package com.lingxi.workbench.admin.skill.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.skill.model.entity.SkillDraftEntity;
import com.lingxi.workbench.admin.skill.repo.mapper.SkillDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Skill 草稿数据仓库。 */
@Repository
@RequiredArgsConstructor
public class SkillDraftRepo {

    private final SkillDraftMapper mapper;

    public Optional<SkillDraftEntity> findBySkillId(Long skillId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<SkillDraftEntity>lambdaQuery()
                .eq(SkillDraftEntity::getSkillId, skillId)));
    }

    public Optional<SkillDraftEntity> findBySkillIdForUpdate(Long skillId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<SkillDraftEntity>lambdaQuery()
                .eq(SkillDraftEntity::getSkillId, skillId)
                .last("FOR UPDATE")));
    }

    public List<SkillDraftEntity> findAllBySkillIds(List<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<SkillDraftEntity>lambdaQuery()
                .in(SkillDraftEntity::getSkillId, skillIds));
    }

    public void insert(SkillDraftEntity entity) {
        mapper.insert(entity);
    }

    public boolean updateWithRevision(SkillDraftEntity entity, Long expectedRevision) {
        return mapper.updateWithRevision(entity, expectedRevision) == 1;
    }

    public void updatePublicationBase(Long draftId, Long versionId, Long operatorId, Long expectedRevision) {
        int affected = mapper.update(
                null,
                Wrappers.<SkillDraftEntity>lambdaUpdate()
                        .set(SkillDraftEntity::getBaseVersionId, versionId)
                        .set(SkillDraftEntity::getUpdatedBy, operatorId)
                        .set(SkillDraftEntity::getRevision, expectedRevision + 1)
                        .set(SkillDraftEntity::getUpdatedAt, OffsetDateTime.now())
                        .eq(SkillDraftEntity::getId, draftId)
                        .eq(SkillDraftEntity::getRevision, expectedRevision));
        if (affected != 1) {
            throw new IllegalStateException("Skill草稿发布基线更新失败");
        }
    }
}
