package com.lingxi.workbench.admin.prompt.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.repo.mapper.PromptVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Prompt 版本数据仓库。 */
@Repository
@RequiredArgsConstructor
public class PromptVersionRepo {

    private final PromptVersionMapper mapper;

    public Optional<PromptVersionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<PromptVersionEntity> findByPromptIdAndId(Long promptId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PromptVersionEntity>lambdaQuery()
                .eq(PromptVersionEntity::getPromptId, promptId)
                .eq(PromptVersionEntity::getId, versionId)));
    }

    public Optional<PromptVersionEntity> findDraft(Long promptId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PromptVersionEntity>lambdaQuery()
                .eq(PromptVersionEntity::getPromptId, promptId)
                .eq(PromptVersionEntity::getStatus, PromptVersionStatus.DRAFT)));
    }

    public List<PromptVersionEntity> findAll(Long promptId) {
        return mapper.selectList(Wrappers.<PromptVersionEntity>lambdaQuery()
                .eq(PromptVersionEntity::getPromptId, promptId)
                .orderByDesc(PromptVersionEntity::getVersionNo));
    }

    /** 批量查询多个 Prompt 的版本，避免列表页逐条访问数据库。 */
    public List<PromptVersionEntity> findAllByPromptIds(List<Long> promptIds) {
        if (promptIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<PromptVersionEntity>lambdaQuery()
                .in(PromptVersionEntity::getPromptId, promptIds)
                .orderByDesc(PromptVersionEntity::getVersionNo));
    }

    public int nextVersionNo(Long promptId) {
        PromptVersionEntity latest = mapper.selectOne(Wrappers.<PromptVersionEntity>lambdaQuery()
                .select(PromptVersionEntity::getVersionNo)
                .eq(PromptVersionEntity::getPromptId, promptId)
                .orderByDesc(PromptVersionEntity::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    public void save(PromptVersionEntity entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
