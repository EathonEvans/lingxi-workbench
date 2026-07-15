package com.lingxi.workbench.admin.department.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.department.model.entity.AdminDepartmentEntity;
import com.lingxi.workbench.admin.department.repo.mapper.AdminDepartmentMapper;
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
public class AdminDepartmentRepo {

    private final AdminDepartmentMapper adminDepartmentMapper;

    public List<AdminDepartmentEntity> findAll() {
        return adminDepartmentMapper.selectList(
                Wrappers.<AdminDepartmentEntity>lambdaQuery()
                        .orderByAsc(AdminDepartmentEntity::getSortOrder, AdminDepartmentEntity::getId)
        );
    }

    public Optional<AdminDepartmentEntity> findById(Long id) {
        return Optional.ofNullable(adminDepartmentMapper.selectById(id));
    }

    public boolean existsByCode(String code) {
        return adminDepartmentMapper.exists(
                Wrappers.<AdminDepartmentEntity>lambdaQuery().eq(AdminDepartmentEntity::getCode, code)
        );
    }

    public boolean existsChildren(Long parentId) {
        return adminDepartmentMapper.exists(
                Wrappers.<AdminDepartmentEntity>lambdaQuery().eq(AdminDepartmentEntity::getParentId, parentId)
        );
    }

    public boolean existsAll(Set<Long> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        return adminDepartmentMapper.selectCount(
                Wrappers.<AdminDepartmentEntity>lambdaQuery().in(AdminDepartmentEntity::getId, ids)
        ) == ids.size();
    }

    public long countAccounts(Long departmentId) {
        return adminDepartmentMapper.countAccounts(departmentId);
    }

    public long countRoleReferences(Long departmentId) {
        return adminDepartmentMapper.countRoleReferences(departmentId);
    }

    public boolean hasAllDataScope(Long accountId) {
        return adminDepartmentMapper.hasAllDataScope(accountId);
    }

    public Set<Long> findAccessibleDepartmentIds(Long accountId) {
        return Set.copyOf(adminDepartmentMapper.selectAccessibleDepartmentIds(accountId));
    }

    public void insert(AdminDepartmentEntity entity) {
        adminDepartmentMapper.insert(entity);
    }

    public void update(AdminDepartmentEntity entity) {
        adminDepartmentMapper.updateById(entity);
    }

    public void delete(Long id) {
        adminDepartmentMapper.deleteById(id);
    }
}
