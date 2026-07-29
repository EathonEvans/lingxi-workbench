package com.lingxi.workbench.admin.application.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationVersionEntity;
import com.lingxi.workbench.admin.application.repo.mapper.AiApplicationVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Application 发布版本数据仓库，只暴露插入和只读查询以保证版本不可变。 */
@Repository
@RequiredArgsConstructor
public class AiApplicationVersionRepo {
    private final AiApplicationVersionMapper mapper;

    /** 按主键查询版本。 */
    public Optional<AiApplicationVersionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按 Application ID 和版本 ID 联合查询，校验版本归属。 */
    public Optional<AiApplicationVersionEntity> findByApplicationIdAndId(
            Long applicationId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AiApplicationVersionEntity>lambdaQuery()
                        .eq(AiApplicationVersionEntity::getApplicationId, applicationId)
                        .eq(AiApplicationVersionEntity::getId, versionId)));
    }

    /** 查询某个 Application 的所有历史版本。 */
    public List<AiApplicationVersionEntity> findAll(Long applicationId) {
        return mapper.selectList(Wrappers.<AiApplicationVersionEntity>lambdaQuery()
                .eq(AiApplicationVersionEntity::getApplicationId, applicationId)
                .orderByDesc(AiApplicationVersionEntity::getVersionNo));
    }

    /** 批量按版本 ID 列表查询版本。 */
    public List<AiApplicationVersionEntity> findAllByIds(List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<AiApplicationVersionEntity>lambdaQuery()
                .in(AiApplicationVersionEntity::getId, versionIds));
    }

    /** 计算下一个版本号，从已有最大版本号 +1。 */
    public int nextVersionNo(Long applicationId) {
        AiApplicationVersionEntity latest = mapper.selectOne(
                Wrappers.<AiApplicationVersionEntity>lambdaQuery()
                        .select(AiApplicationVersionEntity::getVersionNo)
                        .eq(AiApplicationVersionEntity::getApplicationId, applicationId)
                        .orderByDesc(AiApplicationVersionEntity::getVersionNo)
                        .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    /** 按快照 SHA-256 校验是否已存在重复版本，避免重复发布。 */
    public boolean existsByChecksum(Long applicationId, String checksum) {
        return mapper.exists(Wrappers.<AiApplicationVersionEntity>lambdaQuery()
                .eq(AiApplicationVersionEntity::getApplicationId, applicationId)
                .eq(AiApplicationVersionEntity::getSnapshotChecksum, checksum));
    }

    public void insert(AiApplicationVersionEntity entity) {
        mapper.insert(entity);
    }
}
