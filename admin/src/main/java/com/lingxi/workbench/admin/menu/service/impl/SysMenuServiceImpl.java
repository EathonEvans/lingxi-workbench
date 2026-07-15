package com.lingxi.workbench.admin.menu.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

import com.lingxi.workbench.admin.menu.common.enums.MenuType;
import com.lingxi.workbench.admin.menu.model.converter.SysMenuConverter;
import com.lingxi.workbench.admin.menu.model.entity.SysMenuEntity;
import com.lingxi.workbench.admin.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.menu.model.vo.SysMenuVO;
import com.lingxi.workbench.admin.menu.repo.SysMenuRepo;
import com.lingxi.workbench.admin.menu.service.SysMenuService;
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
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuRepo sysMenuRepo;
    private final SysMenuConverter sysMenuConverter;

    @Override
    public List<SysMenuVO> listTree() {
        return buildTree(sysMenuRepo.findAll(), false);
    }

    @Override
    public List<SysMenuVO> listGrantedTree(Long userId) {
        return buildTree(sysMenuRepo.findGrantedMenus(userId), true);
    }

    @Override
    public List<String> listPermissionCodes(Long userId) {
        return sysMenuRepo.findGrantedMenus(userId).stream()
                .map(SysMenuEntity::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public void validateAssignmentHierarchy(Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }

        List<SysMenuEntity> menus = sysMenuRepo.findByIds(menuIds);
        if (menus.size() != menuIds.size()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "待分配菜单中存在无效菜单");
        }

        boolean hierarchyBroken = menus.stream()
                .anyMatch(menu -> menu.getParentId() != null && !menuIds.contains(menu.getParentId()));
        if (hierarchyBroken) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "菜单权限层级不完整，请同时提交已选节点的父级菜单");
        }
    }

    @Override
    @Transactional
    public SysMenuVO create(CreateMenuRequest request) {
        String permissionCode = normalizeNullable(request.permissionCode());
        validateParent(request.parentId(), null, request.menuType());
        validateMenuFields(request.menuType(), request.parentId(), request.routePath(), permissionCode, null);
        validateUnique(request.code(), permissionCode, null);

        SysMenuEntity entity = sysMenuConverter.toEntity(request);
        normalizeEntity(entity);
        sysMenuRepo.insert(entity);
        return sysMenuConverter.toVO(entity);
    }

    @Override
    @Transactional
    public SysMenuVO update(Long id, UpdateMenuRequest request) {
        SysMenuEntity entity = requireMenu(id);
        String permissionCode = normalizeNullable(request.permissionCode());
        validateParent(request.parentId(), id, request.menuType());
        validateMenuFields(request.menuType(), request.parentId(), request.routePath(), permissionCode, id);
        validateUnique(entity.getCode(), permissionCode, id);

        sysMenuConverter.updateEntity(request, entity);
        normalizeEntity(entity);
        sysMenuRepo.update(entity);
        return sysMenuConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireMenu(id);
        if (sysMenuRepo.existsChildren(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "请先删除当前菜单的子节点");
        }
        if (sysMenuRepo.countRoleReferences(id) > 0) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "菜单已分配给角色，不能直接删除");
        }
        sysMenuRepo.delete(id);
    }

    private void validateUnique(String code, String permissionCode, Long excludeId) {
        if (sysMenuRepo.existsByCode(code, excludeId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "菜单编码已存在");
        }
        if (sysMenuRepo.existsByPermissionCode(permissionCode, excludeId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "权限编码已存在");
        }
    }

    private void validateParent(Long parentId, Long currentId, MenuType menuType) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "菜单不能将自己设置为父节点");
        }

        SysMenuEntity parent = requireMenu(parentId);
        if (parent.getMenuType() == MenuType.BUTTON) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "按钮节点不能作为父节点");
        }
        if (parent.getMenuType() == MenuType.MENU && menuType != MenuType.BUTTON) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "页面节点只能挂载按钮权限");
        }

        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (!visited.add(cursor) || cursor.equals(currentId)) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "菜单父子关系不能形成循环");
            }
            cursor = requireMenu(cursor).getParentId();
        }
    }

    private void validateMenuFields(MenuType menuType, Long parentId, String routePath,
                                    String permissionCode, Long currentId) {
        if (parentId == null && menuType != MenuType.DIRECTORY) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "根节点只能是目录");
        }
        if (menuType == MenuType.MENU && (routePath == null || routePath.isBlank())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "页面菜单必须配置路由地址");
        }
        if (menuType == MenuType.BUTTON) {
            if (parentId == null) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "按钮节点必须归属于页面或目录");
            }
            if (permissionCode == null || permissionCode.isBlank()) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "按钮节点必须配置权限编码");
            }
            if (currentId != null && sysMenuRepo.existsChildren(currentId)) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "存在子节点的菜单不能修改为按钮");
            }
        }
    }

    /** 统一收敛默认值和按钮节点的无效字段。 */
    private static void normalizeEntity(SysMenuEntity entity) {
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

    private SysMenuEntity requireMenu(Long id) {
        return sysMenuRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "菜单不存在"));
    }

    private List<SysMenuVO> buildTree(List<SysMenuEntity> entities, boolean onlyVisibleNavigation) {
        List<SysMenuEntity> filtered = entities.stream()
                .filter(entity -> !onlyVisibleNavigation
                        || (Boolean.TRUE.equals(entity.getVisible()) && entity.getMenuType() != MenuType.BUTTON))
                .toList();

        Map<Long, List<SysMenuEntity>> childrenByParent = new HashMap<>();
        List<SysMenuEntity> roots = new ArrayList<>();
        for (SysMenuEntity entity : filtered) {
            if (entity.getParentId() == null) {
                roots.add(entity);
            } else {
                childrenByParent.computeIfAbsent(entity.getParentId(), ignored -> new ArrayList<>()).add(entity);
            }
        }
        return roots.stream().map(root -> toTreeNode(root, childrenByParent)).toList();
    }

    private SysMenuVO toTreeNode(SysMenuEntity entity, Map<Long, List<SysMenuEntity>> childrenByParent) {
        SysMenuVO base = sysMenuConverter.toVO(entity);
        List<SysMenuVO> children = childrenByParent.getOrDefault(entity.getId(), List.of()).stream()
                .map(child -> toTreeNode(child, childrenByParent))
                .toList();
        return new SysMenuVO(
                base.id(), base.parentId(), base.name(), base.code(), base.menuType(),
                base.routePath(), base.component(), base.icon(), base.permissionCode(),
                base.sortOrder(), base.visible(), base.status(), children
        );
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
