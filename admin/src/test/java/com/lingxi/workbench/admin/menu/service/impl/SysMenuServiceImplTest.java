package com.lingxi.workbench.admin.menu.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.menu.common.enums.MenuType;
import com.lingxi.workbench.admin.menu.model.converter.SysMenuConverter;
import com.lingxi.workbench.admin.menu.model.entity.SysMenuEntity;
import com.lingxi.workbench.admin.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.menu.repo.SysMenuRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 菜单权限编码提取测试。
 */
@ExtendWith(MockitoExtension.class)
class SysMenuServiceImplTest {

    @Mock
    private SysMenuRepo sysMenuRepo;
    @Mock
    private SysMenuConverter sysMenuConverter;
    @InjectMocks
    private SysMenuServiceImpl service;

    @Test
    void shouldReturnDistinctNonBlankPermissionCodes() {
        when(sysMenuRepo.findGrantedMenus(1L)).thenReturn(List.of(
                menu("system:role:list"),
                menu("system:role:list"),
                menu(null),
                menu(" ")
        ));

        assertEquals(List.of("system:role:list"), service.listPermissionCodes(1L));
    }

    @Test
    void shouldRejectRootLevelButton() {
        CreateMenuRequest request = new CreateMenuRequest(
                null, "新增角色", "ROLE_CREATE", MenuType.BUTTON,
                null, null, null, "system:role:create", 10, true, EnableStatus.ACTIVE
        );

        assertThrows(BusinessException.class, () -> service.create(request));
    }

    @Test
    void shouldRejectRootLevelPage() {
        CreateMenuRequest request = new CreateMenuRequest(
                null, "工作台", "DASHBOARD_CONSOLE", MenuType.MENU,
                "/dashboard/console", "dashboard/console/index", null,
                null, 10, true, EnableStatus.ACTIVE
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("根节点只能是目录", exception.getMessage());
    }

    @Test
    void shouldRejectPageNestedUnderPage() {
        SysMenuEntity parent = menu(1L, null);
        parent.setMenuType(MenuType.MENU);
        when(sysMenuRepo.findById(1L)).thenReturn(Optional.of(parent));
        CreateMenuRequest request = new CreateMenuRequest(
                1L, "子页面", "NESTED_PAGE", MenuType.MENU,
                "/nested/page", "nested/page/index", null,
                null, 10, true, EnableStatus.ACTIVE
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("页面节点只能挂载按钮权限", exception.getMessage());
    }

    @Test
    void shouldAcceptContinuousMenuAssignmentHierarchy() {
        Set<Long> ids = Set.of(1L, 2L, 3L);
        when(sysMenuRepo.findByIds(ids)).thenReturn(List.of(
                menu(1L, null),
                menu(2L, 1L),
                menu(3L, 2L)
        ));

        service.validateAssignmentHierarchy(ids);
    }

    @Test
    void shouldRejectMenuAssignmentWithMissingParent() {
        Set<Long> ids = Set.of(1L, 3L);
        when(sysMenuRepo.findByIds(ids)).thenReturn(List.of(
                menu(1L, null),
                menu(3L, 2L)
        ));

        assertThrows(BusinessException.class, () -> service.validateAssignmentHierarchy(ids));
    }

    @Test
    void shouldRejectMenuAssignmentContainingUnknownId() {
        Set<Long> ids = Set.of(1L, 99L);
        when(sysMenuRepo.findByIds(ids)).thenReturn(List.of(menu(1L, null)));

        assertThrows(BusinessException.class, () -> service.validateAssignmentHierarchy(ids));
    }

    private static SysMenuEntity menu(String permissionCode) {
        SysMenuEntity entity = new SysMenuEntity();
        entity.setPermissionCode(permissionCode);
        return entity;
    }

    private static SysMenuEntity menu(Long id, Long parentId) {
        SysMenuEntity entity = new SysMenuEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        return entity;
    }
}
