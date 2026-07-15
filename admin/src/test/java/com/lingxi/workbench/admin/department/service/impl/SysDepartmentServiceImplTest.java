package com.lingxi.workbench.admin.department.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.department.model.converter.SysDepartmentConverter;
import com.lingxi.workbench.admin.department.model.entity.SysDepartmentEntity;
import com.lingxi.workbench.admin.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.department.repo.SysDepartmentRepo;
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
class SysDepartmentServiceImplTest {

    @Mock
    private SysDepartmentRepo sysDepartmentRepo;
    @Mock
    private SysDepartmentConverter sysDepartmentConverter;
    @InjectMocks
    private SysDepartmentServiceImpl service;

    @Test
    void shouldRejectDeletingRootDepartment() {
        SysDepartmentEntity root = new SysDepartmentEntity();
        root.setId(1L);
        root.setCode("ROOT");
        when(sysDepartmentRepo.findById(1L)).thenReturn(Optional.of(root));

        assertThrows(BusinessException.class, () -> service.delete(1L));

        verify(sysDepartmentRepo, never()).delete(1L);
    }

    @Test
    void shouldRejectDisablingRootDepartment() {
        SysDepartmentEntity root = new SysDepartmentEntity();
        root.setId(1L);
        root.setCode("ROOT");
        when(sysDepartmentRepo.findById(1L)).thenReturn(Optional.of(root));

        UpdateDepartmentRequest request = new UpdateDepartmentRequest(
                null, "灵析科技", 0, EnableStatus.DISABLED
        );

        assertThrows(BusinessException.class, () -> service.update(1L, request));
    }
}
