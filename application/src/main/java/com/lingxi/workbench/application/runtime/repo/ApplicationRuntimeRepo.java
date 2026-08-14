package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeApplicationMapper;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeApplicationVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Application 发布快照的运行端只读仓储。
 */
@Repository
@RequiredArgsConstructor
public class ApplicationRuntimeRepo {

    private final RuntimeApplicationMapper applicationMapper;
    private final RuntimeApplicationVersionMapper versionMapper;

    /**
     * 按稳定编码查询状态为 ACTIVE 的 Application。
     */
    public Optional<RuntimeApplicationEntity> findActiveByCode(String code) {
        return Optional.ofNullable(applicationMapper.selectOne(
                Wrappers.<RuntimeApplicationEntity>lambdaQuery()
                        .eq(RuntimeApplicationEntity::getCode, code)
                        .eq(RuntimeApplicationEntity::getStatus, "ACTIVE")));
    }

    /**
     * 查询应用端允许选择的 Application。
     *
     * <p>只暴露启用且已经发布的应用，并设置固定上限，避免应用选择器触发无边界全表查询。</p>
     */
    public List<RuntimeApplicationEntity> findAvailableApplications() {
        return applicationMapper.selectList(
                Wrappers.<RuntimeApplicationEntity>lambdaQuery()
                        .eq(RuntimeApplicationEntity::getStatus, "ACTIVE")
                        .isNotNull(RuntimeApplicationEntity::getPublishedVersionId)
                        .orderByAsc(RuntimeApplicationEntity::getName)
                        .orderByAsc(RuntimeApplicationEntity::getId)
                        .last("LIMIT 100"));
    }

    /**
     * 按主键和 Application 归属查询 ApplicationVersion 实体。
     */
    public Optional<RuntimeApplicationVersionEntity> findVersion(Long applicationId, Long versionId) {
        return Optional.ofNullable(versionMapper.selectOne(
                Wrappers.<RuntimeApplicationVersionEntity>lambdaQuery()
                        .eq(RuntimeApplicationVersionEntity::getId, versionId)
                        .eq(RuntimeApplicationVersionEntity::getApplicationId, applicationId)));
    }

    /**
     * 批量查询候选 Application 的生效版本，供应用选择器过滤不可执行快照。
     */
    public List<RuntimeApplicationVersionEntity> findVersionsByIds(List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        return versionMapper.selectByIds(versionIds);
    }
}
