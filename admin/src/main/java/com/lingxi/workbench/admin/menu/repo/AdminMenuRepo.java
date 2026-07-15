package com.lingxi.workbench.admin.menu.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.menu.model.entity.AdminMenuEntity;
import com.lingxi.workbench.admin.menu.repo.mapper.AdminMenuMapper;
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
public class AdminMenuRepo {

    private final AdminMenuMapper adminMenuMapper;

    public List<AdminMenuEntity> findAll() {
        return adminMenuMapper.selectList(
                Wrappers.<AdminMenuEntity>lambdaQuery()
                        .orderByAsc(AdminMenuEntity::getSortOrder, AdminMenuEntity::getId)
        );
    }

    public List<AdminMenuEntity> findGrantedMenus(Long accountId) {
        return adminMenuMapper.selectGrantedMenus(accountId);
    }

    public Optional<AdminMenuEntity> findById(Long id) {
        return Optional.ofNullable(adminMenuMapper.selectById(id));
    }

    public boolean existsByCode(String code, Long excludeId) {
        return adminMenuMapper.exists(
                Wrappers.<AdminMenuEntity>lambdaQuery()
                        .eq(AdminMenuEntity::getCode, code)
                        .ne(excludeId != null, AdminMenuEntity::getId, excludeId)
        );
    }

    public boolean existsByPermissionCode(String permissionCode, Long excludeId) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return adminMenuMapper.exists(
                Wrappers.<AdminMenuEntity>lambdaQuery()
                        .eq(AdminMenuEntity::getPermissionCode, permissionCode)
                        .ne(excludeId != null, AdminMenuEntity::getId, excludeId)
        );
    }

    public boolean existsChildren(Long parentId) {
        return adminMenuMapper.exists(
                Wrappers.<AdminMenuEntity>lambdaQuery().eq(AdminMenuEntity::getParentId, parentId)
        );
    }

    public boolean existsAll(Set<Long> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        return adminMenuMapper.selectCount(
                Wrappers.<AdminMenuEntity>lambdaQuery().in(AdminMenuEntity::getId, ids)
        ) == ids.size();
    }

    public long countRoleReferences(Long menuId) {
        return adminMenuMapper.countRoleReferences(menuId);
    }

    public void insert(AdminMenuEntity entity) {
        adminMenuMapper.insert(entity);
    }

    public void update(AdminMenuEntity entity) {
        adminMenuMapper.updateById(entity);
    }

    public void delete(Long id) {
        adminMenuMapper.deleteById(id);
    }
}
