package com.lingxi.workbench.admin.department.service.impl;

import com.lingxi.workbench.admin.department.model.bo.AdminDataScopeBO;
import com.lingxi.workbench.admin.department.repo.AdminDepartmentRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多角色数据范围合并测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminDataScopeServiceImplTest {

    @Mock
    private AdminDepartmentRepo adminDepartmentRepo;
    @InjectMocks
    private AdminDataScopeServiceImpl service;

    @Test
    void shouldShortCircuitWhenAccountHasAllDataScope() {
        when(adminDepartmentRepo.hasAllDataScope(1L)).thenReturn(true);

        AdminDataScopeBO result = service.resolve(1L);

        assertTrue(result.allData());
        assertFalse(result.selfOnly());
        verify(adminDepartmentRepo, never()).findAccessibleDepartmentIds(1L);
    }

    @Test
    void shouldMergeAccessibleDepartments() {
        when(adminDepartmentRepo.findAccessibleDepartmentIds(2L)).thenReturn(Set.of(10L, 11L));

        AdminDataScopeBO result = service.resolve(2L);

        assertFalse(result.allData());
        assertFalse(result.selfOnly());
        assertEquals(Set.of(10L, 11L), result.departmentIds());
    }

    @Test
    void shouldFallBackToSelfScopeWhenNoDepartmentIsGranted() {
        when(adminDepartmentRepo.findAccessibleDepartmentIds(3L)).thenReturn(Set.of());

        AdminDataScopeBO result = service.resolve(3L);

        assertTrue(result.selfOnly());
    }
}
