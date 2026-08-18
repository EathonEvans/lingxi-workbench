package com.lingxi.workbench.admin.skill.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.skill.model.entity.SkillVersionEntity;
import com.lingxi.workbench.admin.skill.repo.mapper.SkillVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Skill 发布版本数据仓库。 */
@Repository
@RequiredArgsConstructor
public class SkillVersionRepo {

    private final SkillVersionMapper mapper;

    public Optional<SkillVersionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<SkillVersionEntity> findBySkillIdAndId(Long skillId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<SkillVersionEntity>lambdaQuery()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .eq(SkillVersionEntity::getId, versionId)));
    }

    public List<SkillVersionEntity> findAll(Long skillId) {
        return mapper.selectList(Wrappers.<SkillVersionEntity>lambdaQuery()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .orderByDesc(SkillVersionEntity::getVersionNo));
    }

    public List<SkillVersionEntity> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<SkillVersionEntity>lambdaQuery()
                .in(SkillVersionEntity::getId, ids));
    }

    public boolean existsByChecksum(Long skillId, String checksum) {
        return mapper.exists(Wrappers.<SkillVersionEntity>lambdaQuery()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .eq(SkillVersionEntity::getSnapshotChecksum, checksum));
    }

    public int nextVersionNo(Long skillId) {
        SkillVersionEntity latest = mapper.selectOne(Wrappers.<SkillVersionEntity>lambdaQuery()
                .select(SkillVersionEntity::getVersionNo)
                .eq(SkillVersionEntity::getSkillId, skillId)
                .orderByDesc(SkillVersionEntity::getVersionNo)
                .last("LIMIT 1"));
        return Objects.isNull(latest) ? 1 : latest.getVersionNo() + 1;
    }

    public void insert(SkillVersionEntity entity) {
        mapper.insert(entity);
    }

    public boolean revoke(Long id, Long operatorId, String reason) {
        return mapper.update(
                null,
                Wrappers.<SkillVersionEntity>lambdaUpdate()
                        .set(SkillVersionEntity::getRevokedBy, operatorId)
                        .set(SkillVersionEntity::getRevokedAt, OffsetDateTime.now())
                        .set(SkillVersionEntity::getRevokeReason, reason)
                        .eq(SkillVersionEntity::getId, id)
                        .isNull(SkillVersionEntity::getRevokedAt)) == 1;
    }
}
