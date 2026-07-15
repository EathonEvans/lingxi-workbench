package com.lingxi.workbench.admin.department.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.department.model.entity.SysDepartmentEntity;
import com.lingxi.workbench.admin.department.repo.mapper.SysDepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 部门数据访问仓库。
 */
@Repository
@RequiredArgsConstructor
public class SysDepartmentRepo {

    private final SysDepartmentMapper sysDepartmentMapper;

    public List<SysDepartmentEntity> findAll() {
        return sysDepartmentMapper.selectList(
                Wrappers.<SysDepartmentEntity>lambdaQuery()
                        .orderByAsc(SysDepartmentEntity::getSortOrder, SysDepartmentEntity::getId)
        );
    }

    public Optional<SysDepartmentEntity> findById(Long id) {
        return Optional.ofNullable(sysDepartmentMapper.selectById(id));
    }

    public boolean existsByCode(String code) {
        return sysDepartmentMapper.exists(
                Wrappers.<SysDepartmentEntity>lambdaQuery().eq(SysDepartmentEntity::getCode, code)
        );
    }

    public boolean existsChildren(Long parentId) {
        return sysDepartmentMapper.exists(
                Wrappers.<SysDepartmentEntity>lambdaQuery().eq(SysDepartmentEntity::getParentId, parentId)
        );
    }

    public boolean existsAll(Set<Long> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        return sysDepartmentMapper.selectCount(
                Wrappers.<SysDepartmentEntity>lambdaQuery().in(SysDepartmentEntity::getId, ids)
        ) == ids.size();
    }

    public long countUsers(Long departmentId) {
        return sysDepartmentMapper.countUsers(departmentId);
    }

    public long countRoleReferences(Long departmentId) {
        return sysDepartmentMapper.countRoleReferences(departmentId);
    }

    public boolean hasAllDataScope(Long userId) {
        return sysDepartmentMapper.hasAllDataScope(userId);
    }

    public Set<Long> findAccessibleDepartmentIds(Long userId) {
        return Set.copyOf(sysDepartmentMapper.selectAccessibleDepartmentIds(userId));
    }

    public void insert(SysDepartmentEntity entity) {
        sysDepartmentMapper.insert(entity);
    }

    public void update(SysDepartmentEntity entity) {
        sysDepartmentMapper.updateById(entity);
    }

    public void delete(Long id) {
        sysDepartmentMapper.deleteById(id);
    }
}
