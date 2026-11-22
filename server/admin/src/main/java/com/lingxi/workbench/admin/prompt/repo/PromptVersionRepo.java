package com.lingxi.workbench.admin.prompt.repo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.repo.mapper.PromptVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Prompt版本数据仓库。 */
@Repository
@RequiredArgsConstructor
public class PromptVersionRepo {

    private final PromptVersionMapper mapper;

    /** 按主键查询版本。 */
    public Optional<PromptVersionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按Prompt和版本主键精确查找指定版本。 */
    public Optional<PromptVersionEntity> findByPromptIdAndId(Long promptId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PromptVersionEntity>lambdaQuery()
                .eq(PromptVersionEntity::getPromptId, promptId)
                .eq(PromptVersionEntity::getId, versionId)));
    }

    /** 查询指定Prompt的当前草稿版本。 */
    public Optional<PromptVersionEntity> findDraft(Long promptId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PromptVersionEntity>lambdaQuery()
                .eq(PromptVersionEntity::getPromptId, promptId)
                .eq(PromptVersionEntity::getStatus, PromptVersionStatus.DRAFT)));
    }

    /** 查询指定Prompt的所有历史版本。 */
    public List<PromptVersionEntity> findAll(Long promptId) {
        return mapper.selectList(Wrappers.<PromptVersionEntity>lambdaQuery()
                .eq(PromptVersionEntity::getPromptId, promptId)
                .orderByDesc(PromptVersionEntity::getVersionNo));
    }

    /** 批量查询多个Prompt的版本，避免列表页逐条访问数据库。 */
    public List<PromptVersionEntity> findAllByPromptIds(List<Long> promptIds) {
        if (CollUtil.isEmpty(promptIds)) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<PromptVersionEntity>lambdaQuery()
                .in(PromptVersionEntity::getPromptId, promptIds)
                .orderByDesc(PromptVersionEntity::getVersionNo));
    }

    /** 获取指定Prompt的下一个递增版本号。 */
    public int nextVersionNo(Long promptId) {
        PromptVersionEntity latest = mapper.selectOne(Wrappers.<PromptVersionEntity>lambdaQuery()
                .select(PromptVersionEntity::getVersionNo)
                .eq(PromptVersionEntity::getPromptId, promptId)
                .orderByDesc(PromptVersionEntity::getVersionNo)
                .last("LIMIT 1"));
        return Objects.isNull(latest) ? 1 : latest.getVersionNo() + 1;
    }

    /** 新增或更新版本记录。 */
    public void save(PromptVersionEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
