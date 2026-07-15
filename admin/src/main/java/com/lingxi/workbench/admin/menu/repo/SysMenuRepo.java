package com.lingxi.workbench.admin.menu.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.menu.model.entity.SysMenuEntity;
import com.lingxi.workbench.admin.menu.repo.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 菜单数据访问仓库，统一封装树节点和授权菜单查询。
 */
@Repository
@RequiredArgsConstructor
public class SysMenuRepo {

    private final SysMenuMapper sysMenuMapper;

    public List<SysMenuEntity> findAll() {
        return sysMenuMapper.selectList(
                Wrappers.<SysMenuEntity>lambdaQuery()
                        .orderByAsc(SysMenuEntity::getSortOrder, SysMenuEntity::getId)
        );
    }

    public List<SysMenuEntity> findGrantedMenus(Long userId) {
        return sysMenuMapper.selectGrantedMenus(userId);
    }

    public Optional<SysMenuEntity> findById(Long id) {
        return Optional.ofNullable(sysMenuMapper.selectById(id));
    }

    /** 按主键集合查询菜单，用于校验角色菜单分配参数。 */
    public List<SysMenuEntity> findByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return sysMenuMapper.selectByIds(ids);
    }

    public boolean existsByCode(String code, Long excludeId) {
        return sysMenuMapper.exists(
                Wrappers.<SysMenuEntity>lambdaQuery()
                        .eq(SysMenuEntity::getCode, code)
                        .ne(excludeId != null, SysMenuEntity::getId, excludeId)
        );
    }

    public boolean existsByPermissionCode(String permissionCode, Long excludeId) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return sysMenuMapper.exists(
                Wrappers.<SysMenuEntity>lambdaQuery()
                        .eq(SysMenuEntity::getPermissionCode, permissionCode)
                        .ne(excludeId != null, SysMenuEntity::getId, excludeId)
        );
    }

    public boolean existsChildren(Long parentId) {
        return sysMenuMapper.exists(
                Wrappers.<SysMenuEntity>lambdaQuery().eq(SysMenuEntity::getParentId, parentId)
        );
    }

    public long countRoleReferences(Long menuId) {
        return sysMenuMapper.countRoleReferences(menuId);
    }

    public void insert(SysMenuEntity entity) {
        sysMenuMapper.insert(entity);
    }

    public void update(SysMenuEntity entity) {
        sysMenuMapper.updateById(entity);
    }

    public void delete(Long id) {
        sysMenuMapper.deleteById(id);
    }
}
