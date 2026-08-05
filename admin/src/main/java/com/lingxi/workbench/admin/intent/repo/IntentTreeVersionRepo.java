package com.lingxi.workbench.admin.intent.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeVersionEntity;
import com.lingxi.workbench.admin.intent.repo.mapper.IntentTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 意图树发布版本仓库，只允许插入和只读查询。 */
@Repository
@RequiredArgsConstructor
public class IntentTreeVersionRepo {

    private final IntentTreeVersionMapper mapper;

    public Optional<IntentTreeVersionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<IntentTreeVersionEntity> findByIntentTreeIdAndId(Long intentTreeId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<IntentTreeVersionEntity>lambdaQuery()
                        .eq(IntentTreeVersionEntity::getIntentTreeId, intentTreeId)
                        .eq(IntentTreeVersionEntity::getId, versionId)));
    }

    public List<IntentTreeVersionEntity> findAll(Long intentTreeId) {
        return mapper.selectList(Wrappers.<IntentTreeVersionEntity>lambdaQuery()
                .eq(IntentTreeVersionEntity::getIntentTreeId, intentTreeId)
                .orderByDesc(IntentTreeVersionEntity::getVersionNo));
    }

    public List<IntentTreeVersionEntity> findAllByIds(List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<IntentTreeVersionEntity>lambdaQuery()
                .in(IntentTreeVersionEntity::getId, versionIds));
    }

    public int nextVersionNo(Long intentTreeId) {
        IntentTreeVersionEntity latest = mapper.selectOne(
                Wrappers.<IntentTreeVersionEntity>lambdaQuery()
                        .select(IntentTreeVersionEntity::getVersionNo)
                        .eq(IntentTreeVersionEntity::getIntentTreeId, intentTreeId)
                        .orderByDesc(IntentTreeVersionEntity::getVersionNo)
                        .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    public boolean existsByChecksum(Long intentTreeId, String checksum) {
        return mapper.exists(Wrappers.<IntentTreeVersionEntity>lambdaQuery()
                .eq(IntentTreeVersionEntity::getIntentTreeId, intentTreeId)
                .eq(IntentTreeVersionEntity::getSnapshotChecksum, checksum));
    }

    public void insert(IntentTreeVersionEntity entity) {
        mapper.insert(entity);
    }
}
