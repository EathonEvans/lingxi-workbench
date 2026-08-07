package com.lingxi.workbench.admin.workflow.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowEntity;
import com.lingxi.workbench.admin.workflow.model.request.WorkflowPageQuery;
import com.lingxi.workbench.admin.workflow.repo.mapper.WorkflowMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** Workflow 主记录数据仓库。 */
@Repository
@RequiredArgsConstructor
public class WorkflowRepo {
    private final WorkflowMapper mapper;

    /** 按关键字和状态分页查询 Workflow 列表。 */
    public PageResult<WorkflowEntity> findPage(WorkflowPageQuery query) {
        IPage<WorkflowEntity> page = mapper.selectPage(query.toPage(),
                Wrappers.<WorkflowEntity>lambdaQuery()
                        .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                                .like(WorkflowEntity::getName, query.getKeyword())
                                .or()
                                .like(WorkflowEntity::getCode, query.getKeyword()))
                        .eq(Objects.nonNull(query.getStatus()), WorkflowEntity::getStatus, query.getStatus())
                        .orderByDesc(WorkflowEntity::getUpdatedAt)
                        .orderByDesc(WorkflowEntity::getId));
        return PageResult.convert(page);
    }

    /** 按主键查询 Workflow。 */
    public Optional<WorkflowEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 带行锁按主键查询 Workflow，用于发布等写操作。 */
    public Optional<WorkflowEntity> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<WorkflowEntity>lambdaQuery()
                        .eq(WorkflowEntity::getId, id)
                        .last("FOR UPDATE")));
    }

    /** 校验 Workflow 编码唯一性，支持排除指定记录。 */
    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<WorkflowEntity>lambdaQuery()
                .eq(WorkflowEntity::getCode, code)
                .ne(Objects.nonNull(excludedId), WorkflowEntity::getId, excludedId));
    }

    public void save(WorkflowEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
