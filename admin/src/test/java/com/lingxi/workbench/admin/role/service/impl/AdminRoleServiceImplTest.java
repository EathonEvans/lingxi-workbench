package com.lingxi.workbench.admin.role.service.impl;

import com.lingxi.workbench.admin.department.service.AdminDepartmentService;
import com.lingxi.workbench.admin.menu.service.AdminMenuService;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;
import com.lingxi.workbench.admin.role.model.converter.AdminRoleConverter;
import com.lingxi.workbench.admin.role.model.entity.AdminRoleEntity;
import com.lingxi.workbench.admin.role.repo.AdminRoleRepo;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色分配关键约束测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminRoleServiceImplTest {

    @Mock
    private AdminRoleRepo adminRoleRepo;
    @Mock
    private AdminMenuService adminMenuService;
    @Mock
    private AdminDepartmentService adminDepartmentService;
    @Mock
    private AdminRoleConverter adminRoleConverter;

    private AdminRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminRoleServiceImpl(
                adminRoleRepo, adminMenuService, adminDepartmentService, adminRoleConverter
        );
    }

    @Test
    void shouldRejectDeletingBuiltInRole() {
        AdminRoleEntity rootRole = role(1L, RoleDataScope.ALL, true);
        when(adminRoleRepo.findById(1L)).thenReturn(Optional.of(rootRole));

        assertThrows(BusinessException.class, () -> service.delete(1L));

        verify(adminRoleRepo, never()).delete(1L);
    }

    @Test
    void shouldRejectUnknownMenuBeforeReplacingRelations() {
        when(adminRoleRepo.findById(2L)).thenReturn(Optional.of(role(2L, RoleDataScope.SELF, false)));
        when(adminMenuService.existsAll(Set.of(10L, 11L))).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.assignMenus(2L, Set.of(10L, 11L)));

        verify(adminRoleRepo, never()).replaceRoleMenus(2L, Set.of(10L, 11L));
    }

    @Test
    void shouldAssignDepartmentsOnlyForCustomDataScope() {
        when(adminRoleRepo.findById(2L)).thenReturn(Optional.of(role(2L, RoleDataScope.CUSTOM, false)));
        when(adminDepartmentService.existsAll(Set.of(20L))).thenReturn(true);

        service.assignDepartments(2L, Set.of(20L));

        verify(adminRoleRepo).replaceRoleDepartments(2L, Set.of(20L));
    }

    @Test
    void shouldRejectRemovingTheLastRootAdministrator() {
        AdminRoleEntity rootRole = role(1L, RoleDataScope.ALL, true);
        when(adminRoleRepo.accountExists(100L)).thenReturn(true);
        when(adminRoleRepo.existsAll(Set.of())).thenReturn(true);
        when(adminRoleRepo.findByCodeForUpdate("ROOT")).thenReturn(Optional.of(rootRole));
        when(adminRoleRepo.findRoleCodesByAccountId(100L)).thenReturn(List.of("ROOT"));
        when(adminRoleRepo.countOtherAccountsByRoleCode(100L, "ROOT")).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service.assignAccountRoles(100L, Set.of()));

        verify(adminRoleRepo, never()).replaceAccountRoles(100L, Set.of());
    }

    @Test
    void shouldRejectExplicitMenuAssignmentForRootRole() {
        AdminRoleEntity rootRole = role(1L, RoleDataScope.ALL, true);
        rootRole.setCode("ROOT");
        when(adminRoleRepo.findById(1L)).thenReturn(Optional.of(rootRole));

        assertThrows(BusinessException.class, () -> service.assignMenus(1L, Set.of(10L)));

        verify(adminRoleRepo, never()).replaceRoleMenus(1L, Set.of(10L));
    }

    private static AdminRoleEntity role(Long id, RoleDataScope scope, boolean builtIn) {
        AdminRoleEntity role = new AdminRoleEntity();
        role.setId(id);
        role.setDataScope(scope);
        role.setBuiltIn(builtIn);
        return role;
    }
}
