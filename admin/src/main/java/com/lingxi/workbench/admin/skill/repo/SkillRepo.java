package com.lingxi.workbench.admin.skill.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.skill.model.entity.SkillEntity;
import com.lingxi.workbench.admin.skill.model.request.SkillPageQuery;
import com.lingxi.workbench.admin.skill.repo.mapper.SkillMapper;
import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Skill 主体数据仓库。 */
@Repository
@RequiredArgsConstructor
public class SkillRepo {

    private final SkillMapper mapper;

    /** 分页查询 Skill。 */
    public PageResult<SkillEntity> findPage(SkillPageQuery query, Long operatorId) {
        IPage<SkillEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<SkillEntity>lambdaQuery()
                        .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                                .like(SkillEntity::getName, query.getKeyword())
                                .or()
                                .like(SkillEntity::getCode, query.getKeyword()))
                        .eq(Objects.nonNull(query.getSourceType()), SkillEntity::getSourceType, query.getSourceType())
                        .eq(Objects.nonNull(query.getStatus()), SkillEntity::getStatus, query.getStatus())
                        .eq(Objects.nonNull(query.getSecurityStatus()), SkillEntity::getSecurityStatus, query.getSecurityStatus())
                        .and(wrapper -> wrapper
                                .ne(SkillEntity::getVisibility, SkillVisibility.PRIVATE)
                                .or()
                                .eq(SkillEntity::getOwnerUserId, operatorId))
                        .orderByDesc(SkillEntity::getUpdatedAt)
                        .orderByDesc(SkillEntity::getId));
        return PageResult.convert(page);
    }

    public Optional<SkillEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<SkillEntity> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<SkillEntity>lambdaQuery()
                .eq(SkillEntity::getId, id)
                .last("FOR UPDATE")));
    }

    /** 查询允许全局 Agent 绑定的平台预设 Skill，列表规模由平台上限约束。 */
    public List<SkillEntity> findAllAgentBindable() {
        return mapper.selectList(Wrappers.<SkillEntity>lambdaQuery()
                .eq(SkillEntity::getSourceType, SkillSourceType.SYSTEM_PRESET)
                .eq(SkillEntity::getVisibility, SkillVisibility.SYSTEM)
                .eq(SkillEntity::getStatus, SkillLifecycleStatus.ACTIVE)
                .eq(SkillEntity::getSecurityStatus, SkillSecurityStatus.NORMAL)
                .isNotNull(SkillEntity::getPublishedVersionId)
                .orderByAsc(SkillEntity::getName)
                .orderByAsc(SkillEntity::getId)
                .last("LIMIT 100"));
    }

    /** 批量加载发布校验所需的 Skill 主体，避免逐项查询。 */
    public List<SkillEntity> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }

    public boolean existsByCode(String code) {
        return mapper.exists(Wrappers.<SkillEntity>lambdaQuery().eq(SkillEntity::getCode, code));
    }

    public void save(SkillEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
