package com.lingxi.workbench.admin.department.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.department.model.converter.AdminDepartmentConverter;
import com.lingxi.workbench.admin.department.model.entity.AdminDepartmentEntity;
import com.lingxi.workbench.admin.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.department.repo.AdminDepartmentRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 部门树保护规则测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminDepartmentServiceImplTest {

    @Mock
    private AdminDepartmentRepo adminDepartmentRepo;
    @Mock
    private AdminDepartmentConverter adminDepartmentConverter;
    @InjectMocks
    private AdminDepartmentServiceImpl service;

    @Test
    void shouldRejectDeletingRootDepartment() {
        AdminDepartmentEntity root = new AdminDepartmentEntity();
        root.setId(1L);
        root.setCode("ROOT");
        when(adminDepartmentRepo.findById(1L)).thenReturn(Optional.of(root));

        assertThrows(BusinessException.class, () -> service.delete(1L));

        verify(adminDepartmentRepo, never()).delete(1L);
    }

    @Test
    void shouldRejectDisablingRootDepartment() {
        AdminDepartmentEntity root = new AdminDepartmentEntity();
        root.setId(1L);
        root.setCode("ROOT");
        when(adminDepartmentRepo.findById(1L)).thenReturn(Optional.of(root));

        UpdateDepartmentRequest request = new UpdateDepartmentRequest(
                null, "灵析科技", 0, EnableStatus.DISABLED
        );

        assertThrows(BusinessException.class, () -> service.update(1L, request));
    }
}
