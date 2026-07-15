package com.lingxi.workbench.admin.role.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.role.model.entity.AdminRoleEntity;
import com.lingxi.workbench.admin.role.repo.mapper.AdminRoleMapper;
import com.lingxi.workbench.admin.role.repo.mapper.AdminRoleRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 角色及其关联关系的数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class AdminRoleRepo {

    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleRelationMapper relationMapper;

    public List<AdminRoleEntity> findAll() {
        return adminRoleMapper.selectList(Wrappers.<AdminRoleEntity>lambdaQuery()
                .orderByAsc(AdminRoleEntity::getSortOrder, AdminRoleEntity::getId));
    }

    public Optional<AdminRoleEntity> findById(Long id) {
        return Optional.ofNullable(adminRoleMapper.selectById(id));
    }

    public Optional<AdminRoleEntity> findByCodeForUpdate(String code) {
        return Optional.ofNullable(adminRoleMapper.selectByCodeForUpdate(code));
    }

    public boolean existsByCode(String code) {
        return adminRoleMapper.exists(Wrappers.<AdminRoleEntity>lambdaQuery()
                .eq(AdminRoleEntity::getCode, code));
    }

    public boolean existsAll(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return true;
        }
        return adminRoleMapper.selectCount(Wrappers.<AdminRoleEntity>lambdaQuery()
                .in(AdminRoleEntity::getId, roleIds)) == roleIds.size();
    }

    public List<String> findRoleCodesByAccountId(Long accountId) {
        return adminRoleMapper.selectRoleCodesByAccountId(accountId);
    }

    public boolean accountExists(Long accountId) {
        return relationMapper.countAccount(accountId) > 0;
    }

    public long countRoleAccounts(Long roleId) {
        return relationMapper.countRoleAccounts(roleId);
    }

    public long countOtherAccountsByRoleCode(Long accountId, String roleCode) {
        return relationMapper.countOtherAccountsByRoleCode(accountId, roleCode);
    }

    public Set<Long> findAccountRoleIds(Long accountId) {
        return Set.copyOf(relationMapper.selectAccountRoleIds(accountId));
    }

    public Set<Long> findRoleMenuIds(Long roleId) {
        return Set.copyOf(relationMapper.selectRoleMenuIds(roleId));
    }

    public Set<Long> findRoleDepartmentIds(Long roleId) {
        return Set.copyOf(relationMapper.selectRoleDepartmentIds(roleId));
    }

    public void insert(AdminRoleEntity entity) {
        adminRoleMapper.insert(entity);
    }

    public void update(AdminRoleEntity entity) {
        adminRoleMapper.updateById(entity);
    }

    public void delete(Long id) {
        adminRoleMapper.deleteById(id);
    }

    /** 全量替换账号角色；空集合代表清空账号角色。 */
    public void replaceAccountRoles(Long accountId, Set<Long> roleIds) {
        relationMapper.deleteAccountRoles(accountId);
        if (!roleIds.isEmpty()) {
            relationMapper.insertAccountRoles(accountId, roleIds);
        }
    }

    /** 全量替换角色菜单；空集合代表清空角色菜单。 */
    public void replaceRoleMenus(Long roleId, Set<Long> menuIds) {
        relationMapper.deleteRoleMenus(roleId);
        if (!menuIds.isEmpty()) {
            relationMapper.insertRoleMenus(roleId, menuIds);
        }
    }

    /** 全量替换角色自定义部门范围；空集合代表清空。 */
    public void replaceRoleDepartments(Long roleId, Set<Long> departmentIds) {
        relationMapper.deleteRoleDepartments(roleId);
        if (!departmentIds.isEmpty()) {
            relationMapper.insertRoleDepartments(roleId, departmentIds);
        }
    }
}
