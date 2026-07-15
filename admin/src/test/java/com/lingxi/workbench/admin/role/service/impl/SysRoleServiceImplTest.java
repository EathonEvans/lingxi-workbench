package com.lingxi.workbench.admin.role.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

import com.lingxi.workbench.admin.department.service.SysDepartmentService;
import com.lingxi.workbench.admin.auth.service.SysUserService;
import com.lingxi.workbench.admin.menu.service.SysMenuService;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;
import com.lingxi.workbench.admin.role.model.converter.SysRoleConverter;
import com.lingxi.workbench.admin.role.model.entity.SysRoleEntity;
import com.lingxi.workbench.admin.role.repo.SysRoleRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色分配关键约束测试。
 */
@ExtendWith(MockitoExtension.class)
class SysRoleServiceImplTest {

    @Mock
    private SysRoleRepo sysRoleRepo;
    @Mock
    private SysMenuService sysMenuService;
    @Mock
    private SysDepartmentService sysDepartmentService;
    @Mock
    private SysRoleConverter sysRoleConverter;
    @Mock
    private SysUserService sysUserService;

    private SysRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysRoleServiceImpl(
                sysRoleRepo, sysMenuService, sysDepartmentService, sysRoleConverter,
                sysUserService
        );
    }

    @Test
    void shouldRejectDeletingBuiltInRole() {
        SysRoleEntity rootRole = role(1L, RoleDataScope.ALL, true);
        when(sysRoleRepo.findById(1L)).thenReturn(Optional.of(rootRole));

        assertThrows(BusinessException.class, () -> service.delete(1L));

        verify(sysRoleRepo, never()).delete(1L);
    }

    @Test
    void shouldRejectUnknownMenuBeforeReplacingRelations() {
        when(sysRoleRepo.findById(2L)).thenReturn(Optional.of(role(2L, RoleDataScope.SELF, false)));
        doThrow(new BusinessException(HttpStatusEnum.BAD_REQUEST, "待分配菜单中存在无效菜单"))
                .when(sysMenuService).validateAssignmentHierarchy(Set.of(10L, 11L));

        assertThrows(BusinessException.class, () -> service.assignMenus(2L, Set.of(10L, 11L)));

        verify(sysRoleRepo, never()).replaceRoleMenus(2L, Set.of(10L, 11L));
    }

    @Test
    void shouldPersistSubmittedMenusWithoutChangingIds() {
        Set<Long> submittedIds = Set.of(10L, 11L, 12L);
        when(sysRoleRepo.findById(2L)).thenReturn(Optional.of(role(2L, RoleDataScope.SELF, false)));

        service.assignMenus(2L, submittedIds);

        verify(sysMenuService).validateAssignmentHierarchy(submittedIds);
        verify(sysRoleRepo).replaceRoleMenus(2L, submittedIds);
    }

    @Test
    void shouldAssignDepartmentsOnlyForCustomDataScope() {
        when(sysRoleRepo.findById(2L)).thenReturn(Optional.of(role(2L, RoleDataScope.CUSTOM, false)));
        when(sysDepartmentService.existsAll(Set.of(20L))).thenReturn(true);

        service.assignDepartments(2L, Set.of(20L));

        verify(sysRoleRepo).replaceRoleDepartments(2L, Set.of(20L));
    }

    @Test
    void shouldRejectRemovingTheLastRootSysistrator() {
        SysRoleEntity rootRole = role(1L, RoleDataScope.ALL, true);
        when(sysRoleRepo.userExists(100L)).thenReturn(true);
        when(sysRoleRepo.existsAll(Set.of())).thenReturn(true);
        when(sysRoleRepo.findByCodeForUpdate("ROOT")).thenReturn(Optional.of(rootRole));
        when(sysRoleRepo.findRoleCodesByUserId(100L)).thenReturn(List.of("ROOT"));
        when(sysRoleRepo.countOtherUsersByRoleCode(100L, "ROOT")).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service.assignUserRoles(100L, Set.of()));

        verify(sysRoleRepo, never()).replaceUserRoles(100L, Set.of());
    }

    @Test
    void shouldRejectExplicitMenuAssignmentForRootRole() {
        SysRoleEntity rootRole = role(1L, RoleDataScope.ALL, true);
        rootRole.setCode("ROOT");
        when(sysRoleRepo.findById(1L)).thenReturn(Optional.of(rootRole));

        assertThrows(BusinessException.class, () -> service.assignMenus(1L, Set.of(10L)));

        verify(sysRoleRepo, never()).replaceRoleMenus(1L, Set.of(10L));
    }

    private static SysRoleEntity role(Long id, RoleDataScope scope, boolean builtIn) {
        SysRoleEntity role = new SysRoleEntity();
        role.setId(id);
        role.setDataScope(scope);
        role.setBuiltIn(builtIn);
        return role;
    }
}
