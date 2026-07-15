package com.lingxi.workbench.admin.department.service.impl;

import com.lingxi.workbench.admin.department.model.bo.SysDataScopeBO;
import com.lingxi.workbench.admin.department.repo.SysDepartmentRepo;
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
class SysDataScopeServiceImplTest {

    @Mock
    private SysDepartmentRepo sysDepartmentRepo;
    @InjectMocks
    private SysDataScopeServiceImpl service;

    @Test
    void shouldShortCircuitWhenUserHasAllDataScope() {
        when(sysDepartmentRepo.hasAllDataScope(1L)).thenReturn(true);

        SysDataScopeBO result = service.resolve(1L);

        assertTrue(result.allData());
        assertFalse(result.selfOnly());
        verify(sysDepartmentRepo, never()).findAccessibleDepartmentIds(1L);
    }

    @Test
    void shouldMergeAccessibleDepartments() {
        when(sysDepartmentRepo.findAccessibleDepartmentIds(2L)).thenReturn(Set.of(10L, 11L));

        SysDataScopeBO result = service.resolve(2L);

        assertFalse(result.allData());
        assertFalse(result.selfOnly());
        assertEquals(Set.of(10L, 11L), result.departmentIds());
    }

    @Test
    void shouldFallBackToSelfScopeWhenNoDepartmentIsGranted() {
        when(sysDepartmentRepo.findAccessibleDepartmentIds(3L)).thenReturn(Set.of());

        SysDataScopeBO result = service.resolve(3L);

        assertTrue(result.selfOnly());
    }
}
