package com.lingxi.workbench.admin.sys.menu.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

import com.lingxi.workbench.admin.common.enums.MenuType;
import com.lingxi.workbench.admin.sys.menu.model.converter.SysMenuConverter;
import com.lingxi.workbench.admin.sys.menu.model.entity.SysMenuEntity;
import com.lingxi.workbench.admin.sys.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.vo.SysMenuVO;
import com.lingxi.workbench.admin.sys.menu.repo.SysMenuRepo;
import com.lingxi.workbench.admin.sys.menu.service.SysMenuService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 菜单管理实现，负责树结构、节点合法性和权限编码约束。
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuRepo sysMenuRepo;
    private final SysMenuConverter sysMenuConverter;

    /**
     * 查询管理页面使用的完整菜单树，包含停用节点和按钮节点。
     *
     * @return 菜单树根节点列表
     */
    @Override
    public List<SysMenuVO> listTree() {
        return buildTree(sysMenuRepo.findAll(), false);
    }

    /**
     * 查询指定管理员可见的启用导航树，不返回按钮节点和隐藏节点。
     *
     * @param userId 管理员主键
     * @return 授权导航树
     */
    @Override
    public List<SysMenuVO> listGrantedTree(Long userId) {
        return buildTree(sysMenuRepo.findGrantedMenus(userId), true);
    }

    /**
     * 查询指定管理员拥有的去重权限编码列表。
     *
     * @param userId 管理员主键
     * @return 权限编码列表
     */
    @Override
    public List<String> listPermissionCodes(Long userId) {
        return sysMenuRepo.findGrantedMenus(userId).stream()
                .map(SysMenuEntity::getPermissionCode)
                .filter(CharSequenceUtil::isNotBlank)
                .distinct()
                .toList();
    }

    /**
     * 校验角色菜单分配集合中的主键有效性和父子层级连续性。
     * 子节点被选中时，其父节点必须同时存在于集合中。
     *
     * @param menuIds 菜单主键集合
     * @throws BusinessException 包含无效菜单或层级不完整时抛出
     */
    @Override
    public void validateAssignmentHierarchy(Set<Long> menuIds) {
        if (CollUtil.isEmpty(menuIds)) {
            return;
        }

        List<SysMenuEntity> menus = sysMenuRepo.findByIds(menuIds);
        if (menus.size() != menuIds.size()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "待分配菜单中存在无效菜单");
        }

        boolean hierarchyBroken = menus.stream()
                .anyMatch(menu -> Objects.nonNull(menu.getParentId()) && !menuIds.contains(menu.getParentId()));
        if (hierarchyBroken) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "菜单权限层级不完整，请同时提交已选节点的父级菜单");
        }
    }

    /**
     * 新增目录、页面菜单或按钮权限，校验层级合法性和编码唯一性。
     *
     * @param request 新增菜单请求
     * @return 新增后的菜单视图
     */
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

    /**
     * 修改菜单节点，菜单编码创建后保持不变，校验层级合法性和编码唯一性。
     *
     * @param id      菜单主键
     * @param request 修改请求
     * @return 修改后的菜单视图
     */
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

    /**
     * 删除菜单，需确保无子节点且未被角色引用。
     *
     * @param id 菜单主键
     */
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

    /**
     * 校验菜单编码和权限编码的唯一性。
     */
    private void validateUnique(String code, String permissionCode, Long excludeId) {
        if (sysMenuRepo.existsByCode(code, excludeId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "菜单编码已存在");
        }
        if (sysMenuRepo.existsByPermissionCode(permissionCode, excludeId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "权限编码已存在");
        }
    }

    /**
     * 校验父节点合法性：父节点必须存在，按钮不能作为父节点，页面节点只能挂载按钮。
     * 同时沿父链向上检查是否存在循环引用。
     */
    private void validateParent(Long parentId, Long currentId, MenuType menuType) {
        if (Objects.isNull(parentId)) {
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
        while (Objects.nonNull(cursor)) {
            if (!visited.add(cursor) || cursor.equals(currentId)) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "菜单父子关系不能形成循环");
            }
            cursor = requireMenu(cursor).getParentId();
        }
    }

    /**
     * 按菜单类型校验字段约束：根节点只能是目录，页面菜单必须配置路由，按钮节点必须配置权限编码。
     */
    private void validateMenuFields(MenuType menuType, Long parentId, String routePath,
                                    String permissionCode, Long currentId) {
        if (Objects.isNull(parentId) && menuType != MenuType.DIRECTORY) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "根节点只能是目录");
        }
        if (menuType == MenuType.MENU && CharSequenceUtil.isBlank(routePath)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "页面菜单必须配置路由地址");
        }
        if (menuType == MenuType.BUTTON) {
            if (Objects.isNull(parentId)) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "按钮节点必须归属于页面或目录");
            }
            if (CharSequenceUtil.isBlank(permissionCode)) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "按钮节点必须配置权限编码");
            }
            if (Objects.nonNull(currentId) && sysMenuRepo.existsChildren(currentId)) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "存在子节点的菜单不能修改为按钮");
            }
        }
    }

    /**
     * 统一收敛默认值：空字符串转为 null、排序序号默认为 0、按钮节点清除路由和组件字段。
     */
    private static void normalizeEntity(SysMenuEntity entity) {
        entity.setName(entity.getName().trim());
        entity.setRoutePath(normalizeNullable(entity.getRoutePath()));
        entity.setComponent(normalizeNullable(entity.getComponent()));
        entity.setIcon(normalizeNullable(entity.getIcon()));
        entity.setPermissionCode(normalizeNullable(entity.getPermissionCode()));
        entity.setSortOrder(Objects.isNull(entity.getSortOrder()) ? 0 : entity.getSortOrder());
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

    /**
     * 将扁平菜单列表构建为树结构。
     * 当 onlyVisibleNavigation 为 true 时，过滤掉按钮节点和不可见节点。
     */
    private List<SysMenuVO> buildTree(List<SysMenuEntity> entities, boolean onlyVisibleNavigation) {
        List<SysMenuEntity> filtered = entities.stream()
                .filter(entity -> !onlyVisibleNavigation
                        || (Boolean.TRUE.equals(entity.getVisible()) && entity.getMenuType() != MenuType.BUTTON))
                .toList();

        Map<Long, List<SysMenuEntity>> childrenByParent = new HashMap<>();
        List<SysMenuEntity> roots = new ArrayList<>();
        for (SysMenuEntity entity : filtered) {
            if (Objects.isNull(entity.getParentId())) {
                roots.add(entity);
            } else {
                childrenByParent.computeIfAbsent(entity.getParentId(), ignored -> new ArrayList<>()).add(entity);
            }
        }
        return roots.stream().map(root -> toTreeNode(root, childrenByParent)).toList();
    }

    /**
     * 递归构建菜单树节点，将实体转换为视图并填充子菜单列表。
     */
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

    /**
     * 将空白字符串统一转为 null，保留有效值并去除首尾空格。
     */
    private static String normalizeNullable(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }
}
