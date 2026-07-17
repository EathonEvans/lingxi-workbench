package com.lingxi.workbench.admin.sys.role.repo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.sys.role.model.entity.SysRoleEntity;
import com.lingxi.workbench.admin.sys.role.repo.mapper.SysRoleMapper;
import com.lingxi.workbench.admin.sys.role.repo.mapper.SysRoleRelationMapper;
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
public class SysRoleRepo {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleRelationMapper relationMapper;

    public List<SysRoleEntity> findAll() {
        return sysRoleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                .orderByAsc(SysRoleEntity::getSortOrder, SysRoleEntity::getId));
    }

    public Optional<SysRoleEntity> findById(Long id) {
        return Optional.ofNullable(sysRoleMapper.selectById(id));
    }

    public Optional<SysRoleEntity> findByCodeForUpdate(String code) {
        return Optional.ofNullable(sysRoleMapper.selectByCodeForUpdate(code));
    }

    public boolean existsByCode(String code) {
        return sysRoleMapper.exists(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(SysRoleEntity::getCode, code));
    }

    public boolean existsAll(Set<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return true;
        }
        return sysRoleMapper.selectCount(Wrappers.<SysRoleEntity>lambdaQuery()
                .in(SysRoleEntity::getId, roleIds)) == roleIds.size();
    }

    public List<String> findRoleCodesByUserId(Long userId) {
        return sysRoleMapper.selectRoleCodesByUserId(userId);
    }

    public boolean userExists(Long userId) {
        return relationMapper.countUser(userId) > 0;
    }

    public long countRoleUsers(Long roleId) {
        return relationMapper.countRoleUsers(roleId);
    }

    public long countOtherUsersByRoleCode(Long userId, String roleCode) {
        return relationMapper.countOtherUsersByRoleCode(userId, roleCode);
    }

    public Set<Long> findUserRoleIds(Long userId) {
        return Set.copyOf(relationMapper.selectUserRoleIds(userId));
    }

    public Set<Long> findRoleMenuIds(Long roleId) {
        return Set.copyOf(relationMapper.selectRoleMenuIds(roleId));
    }

    public Set<Long> findRoleDepartmentIds(Long roleId) {
        return Set.copyOf(relationMapper.selectRoleDepartmentIds(roleId));
    }

    public void insert(SysRoleEntity entity) {
        sysRoleMapper.insert(entity);
    }

    public void update(SysRoleEntity entity) {
        sysRoleMapper.updateById(entity);
    }

    public void delete(Long id) {
        sysRoleMapper.deleteById(id);
    }

    /** 全量替换账号角色；空集合代表清空账号角色。 */
    public void replaceUserRoles(Long userId, Set<Long> roleIds) {
        relationMapper.deleteUserRoles(userId);
        if (CollUtil.isNotEmpty(roleIds)) {
            relationMapper.insertUserRoles(userId, roleIds);
        }
    }

    /** 全量替换角色菜单；空集合代表清空角色菜单。 */
    public void replaceRoleMenus(Long roleId, Set<Long> menuIds) {
        relationMapper.deleteRoleMenus(roleId);
        if (CollUtil.isNotEmpty(menuIds)) {
            relationMapper.insertRoleMenus(roleId, menuIds);
        }
    }

    /** 全量替换角色自定义部门范围；空集合代表清空。 */
    public void replaceRoleDepartments(Long roleId, Set<Long> departmentIds) {
        relationMapper.deleteRoleDepartments(roleId);
        if (CollUtil.isNotEmpty(departmentIds)) {
            relationMapper.insertRoleDepartments(roleId, departmentIds);
        }
    }
}
