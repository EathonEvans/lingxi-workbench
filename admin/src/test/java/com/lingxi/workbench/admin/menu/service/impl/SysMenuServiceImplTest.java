package com.lingxi.workbench.admin.menu.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.MenuType;
import com.lingxi.workbench.admin.sys.menu.model.converter.SysMenuConverter;
import com.lingxi.workbench.admin.sys.menu.model.entity.SysMenuEntity;
import com.lingxi.workbench.admin.sys.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.repo.SysMenuRepo;
import com.lingxi.workbench.admin.sys.menu.service.impl.SysMenuServiceImpl;
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
        CreateMenuRequest request = CreateMenuRequest.builder()
                .parentId(null).name("新增角色").code("ROLE_CREATE").menuType(MenuType.BUTTON)
                .routePath(null).component(null).icon(null)
                .permissionCode("system:role:create").sortOrder(10).visible(true)
                .status(EnableStatus.ACTIVE).build();

        assertThrows(BusinessException.class, () -> service.create(request));
    }

    @Test
    void shouldRejectRootLevelPage() {
        CreateMenuRequest request = CreateMenuRequest.builder()
                .parentId(null).name("工作台").code("DASHBOARD_CONSOLE").menuType(MenuType.MENU)
                .routePath("/dashboard/console").component("dashboard/console/index").icon(null)
                .permissionCode(null).sortOrder(10).visible(true)
                .status(EnableStatus.ACTIVE).build();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("根节点只能是目录", exception.getMessage());
    }

    @Test
    void shouldRejectPageNestedUnderPage() {
        SysMenuEntity parent = menu(1L, null);
        parent.setMenuType(MenuType.MENU);
        when(sysMenuRepo.findById(1L)).thenReturn(Optional.of(parent));
        CreateMenuRequest request = CreateMenuRequest.builder()
                .parentId(1L).name("子页面").code("NESTED_PAGE").menuType(MenuType.MENU)
                .routePath("/nested/page").component("nested/page/index").icon(null)
                .permissionCode(null).sortOrder(10).visible(true)
                .status(EnableStatus.ACTIVE).build();

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
