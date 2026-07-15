package com.lingxi.workbench.admin.menu.service.impl;

import com.lingxi.workbench.admin.menu.common.enums.MenuType;
import com.lingxi.workbench.admin.menu.model.converter.AdminMenuConverter;
import com.lingxi.workbench.admin.menu.model.entity.AdminMenuEntity;
import com.lingxi.workbench.admin.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.menu.model.vo.AdminMenuVO;
import com.lingxi.workbench.admin.menu.repo.AdminMenuRepo;
import com.lingxi.workbench.admin.menu.service.AdminMenuService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单管理实现，负责树结构、节点合法性和权限编码约束。
 */
@Service
@RequiredArgsConstructor
public class AdminMenuServiceImpl implements AdminMenuService {

    private final AdminMenuRepo adminMenuRepo;
    private final AdminMenuConverter adminMenuConverter;

    @Override
    public List<AdminMenuVO> listTree() {
        return buildTree(adminMenuRepo.findAll(), false);
    }

    @Override
    public List<AdminMenuVO> listGrantedTree(Long accountId) {
        return buildTree(adminMenuRepo.findGrantedMenus(accountId), true);
    }

    @Override
    public List<String> listPermissionCodes(Long accountId) {
        return adminMenuRepo.findGrantedMenus(accountId).stream()
                .map(AdminMenuEntity::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public boolean existsAll(Set<Long> menuIds) {
        return adminMenuRepo.existsAll(menuIds);
    }

    @Override
    @Transactional
    public AdminMenuVO create(CreateMenuRequest request) {
        String permissionCode = normalizeNullable(request.permissionCode());
        validateParent(request.parentId(), null);
        validateMenuFields(request.menuType(), request.parentId(), request.routePath(), permissionCode, null);
        validateUnique(request.code(), permissionCode, null);

        AdminMenuEntity entity = adminMenuConverter.toEntity(request);
        normalizeEntity(entity);
        adminMenuRepo.insert(entity);
        return adminMenuConverter.toVO(entity);
    }

    @Override
    @Transactional
    public AdminMenuVO update(Long id, UpdateMenuRequest request) {
        AdminMenuEntity entity = requireMenu(id);
        String permissionCode = normalizeNullable(request.permissionCode());
        validateParent(request.parentId(), id);
        validateMenuFields(request.menuType(), request.parentId(), request.routePath(), permissionCode, id);
        validateUnique(entity.getCode(), permissionCode, id);

        adminMenuConverter.updateEntity(request, entity);
        normalizeEntity(entity);
        adminMenuRepo.update(entity);
        return adminMenuConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireMenu(id);
        if (adminMenuRepo.existsChildren(id)) {
            throw new BusinessException(409, 409, "请先删除当前菜单的子节点");
        }
        if (adminMenuRepo.countRoleReferences(id) > 0) {
            throw new BusinessException(409, 409, "菜单已分配给角色，不能直接删除");
        }
        adminMenuRepo.delete(id);
    }

    private void validateUnique(String code, String permissionCode, Long excludeId) {
        if (adminMenuRepo.existsByCode(code, excludeId)) {
            throw new BusinessException(409, 409, "菜单编码已存在");
        }
        if (adminMenuRepo.existsByPermissionCode(permissionCode, excludeId)) {
            throw new BusinessException(409, 409, "权限编码已存在");
        }
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(400, 400, "菜单不能将自己设置为父节点");
        }

        AdminMenuEntity parent = requireMenu(parentId);
        if (parent.getMenuType() == MenuType.BUTTON) {
            throw new BusinessException(400, 400, "按钮节点不能作为父节点");
        }

        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (!visited.add(cursor) || cursor.equals(currentId)) {
                throw new BusinessException(400, 400, "菜单父子关系不能形成循环");
            }
            cursor = requireMenu(cursor).getParentId();
        }
    }

    private void validateMenuFields(MenuType menuType, Long parentId, String routePath,
                                    String permissionCode, Long currentId) {
        if (menuType == MenuType.MENU && (routePath == null || routePath.isBlank())) {
            throw new BusinessException(400, 400, "页面菜单必须配置路由地址");
        }
        if (menuType == MenuType.BUTTON) {
            if (parentId == null) {
                throw new BusinessException(400, 400, "按钮节点必须归属于页面或目录");
            }
            if (permissionCode == null || permissionCode.isBlank()) {
                throw new BusinessException(400, 400, "按钮节点必须配置权限编码");
            }
            if (currentId != null && adminMenuRepo.existsChildren(currentId)) {
                throw new BusinessException(409, 409, "存在子节点的菜单不能修改为按钮");
            }
        }
    }

    /** 统一收敛默认值和按钮节点的无效字段。 */
    private static void normalizeEntity(AdminMenuEntity entity) {
        entity.setName(entity.getName().trim());
        entity.setRoutePath(normalizeNullable(entity.getRoutePath()));
        entity.setComponent(normalizeNullable(entity.getComponent()));
        entity.setIcon(normalizeNullable(entity.getIcon()));
        entity.setPermissionCode(normalizeNullable(entity.getPermissionCode()));
        entity.setSortOrder(entity.getSortOrder() == null ? 0 : entity.getSortOrder());
        if (entity.getMenuType() == MenuType.BUTTON) {
            entity.setRoutePath(null);
            entity.setComponent(null);
            entity.setVisible(false);
        }
    }

    private AdminMenuEntity requireMenu(Long id) {
        return adminMenuRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, 404, "菜单不存在"));
    }

    private List<AdminMenuVO> buildTree(List<AdminMenuEntity> entities, boolean onlyVisibleNavigation) {
        List<AdminMenuEntity> filtered = entities.stream()
                .filter(entity -> !onlyVisibleNavigation
                        || (Boolean.TRUE.equals(entity.getVisible()) && entity.getMenuType() != MenuType.BUTTON))
                .toList();

        Map<Long, List<AdminMenuEntity>> childrenByParent = new HashMap<>();
        List<AdminMenuEntity> roots = new ArrayList<>();
        for (AdminMenuEntity entity : filtered) {
            if (entity.getParentId() == null) {
                roots.add(entity);
            } else {
                childrenByParent.computeIfAbsent(entity.getParentId(), ignored -> new ArrayList<>()).add(entity);
            }
        }
        return roots.stream().map(root -> toTreeNode(root, childrenByParent)).toList();
    }

    private AdminMenuVO toTreeNode(AdminMenuEntity entity, Map<Long, List<AdminMenuEntity>> childrenByParent) {
        AdminMenuVO base = adminMenuConverter.toVO(entity);
        List<AdminMenuVO> children = childrenByParent.getOrDefault(entity.getId(), List.of()).stream()
                .map(child -> toTreeNode(child, childrenByParent))
                .toList();
        return new AdminMenuVO(
                base.id(), base.parentId(), base.name(), base.code(), base.menuType(),
                base.routePath(), base.component(), base.icon(), base.permissionCode(),
                base.sortOrder(), base.visible(), base.status(), children
        );
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
