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

    /** 查询角色列表（上限 1000 防止字典表失控），按排序序号和主键升序。 */
    public List<SysRoleEntity> findAll() {
        return sysRoleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                .orderByAsc(SysRoleEntity::getSortOrder, SysRoleEntity::getId)
                .last("LIMIT 1000"));
    }

    /** 按主键查询角色。 */
    public Optional<SysRoleEntity> findById(Long id) {
        return Optional.ofNullable(sysRoleMapper.selectById(id));
    }

    /** 按编码查询并锁定角色行，用于 ROOT 管理员串行化校验。 */
    public Optional<SysRoleEntity> findByCodeForUpdate(String code) {
        return Optional.ofNullable(sysRoleMapper.selectByCodeForUpdate(code));
    }

    /** 判断角色编码是否已存在。 */
    public boolean existsByCode(String code) {
        return sysRoleMapper.exists(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(SysRoleEntity::getCode, code));
    }

    /** 判断给定的角色主键集合是否全部存在，空集合视为全部存在。 */
    public boolean existsAll(Set<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return true;
        }
        return sysRoleMapper.selectCount(Wrappers.<SysRoleEntity>lambdaQuery()
                .in(SysRoleEntity::getId, roleIds)) == roleIds.size();
    }

    /** 查询用户当前生效的角色编码列表。 */
    public List<String> findRoleCodesByUserId(Long userId) {
        return sysRoleMapper.selectRoleCodesByUserId(userId);
    }

    /** 判断系统用户是否存在。 */
    public boolean hasUser(Long userId) {
        return relationMapper.countUser(userId) > 0;
    }

    /** 统计拥有指定角色的管理员数量。 */
    public long countRoleUsers(Long roleId) {
        return relationMapper.countRoleUsers(roleId);
    }

    /** 统计除指定用户外仍拥有目标角色编码的其他管理员数量。 */
    public long countOtherUsersByRoleCode(Long userId, String roleCode) {
        return relationMapper.countOtherUsersByRoleCode(userId, roleCode);
    }

    /** 查询用户当前已分配的角色主键集合。 */
    public Set<Long> findUserRoleIds(Long userId) {
        return Set.copyOf(relationMapper.selectUserRoleIds(userId));
    }

    /** 查询角色当前已分配的菜单主键集合。 */
    public Set<Long> findRoleMenuIds(Long roleId) {
        return Set.copyOf(relationMapper.selectRoleMenuIds(roleId));
    }

    /** 查询角色当前已分配的部门主键集合。 */
    public Set<Long> findRoleDepartmentIds(Long roleId) {
        return Set.copyOf(relationMapper.selectRoleDepartmentIds(roleId));
    }

    /** 新增角色。 */
    public void insert(SysRoleEntity entity) {
        sysRoleMapper.insert(entity);
    }

    /** 更新角色。 */
    public void update(SysRoleEntity entity) {
        sysRoleMapper.updateById(entity);
    }

    /** 删除角色。 */
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
