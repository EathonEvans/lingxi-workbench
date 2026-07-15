package com.lingxi.workbench.admin.menu.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.menu.common.enums.MenuType;
import com.lingxi.workbench.admin.menu.model.converter.AdminMenuConverter;
import com.lingxi.workbench.admin.menu.model.entity.AdminMenuEntity;
import com.lingxi.workbench.admin.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.menu.repo.AdminMenuRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 菜单权限编码提取测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminMenuServiceImplTest {

    @Mock
    private AdminMenuRepo adminMenuRepo;
    @Mock
    private AdminMenuConverter adminMenuConverter;
    @InjectMocks
    private AdminMenuServiceImpl service;

    @Test
    void shouldReturnDistinctNonBlankPermissionCodes() {
        when(adminMenuRepo.findGrantedMenus(1L)).thenReturn(List.of(
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

    private static AdminMenuEntity menu(String permissionCode) {
        AdminMenuEntity entity = new AdminMenuEntity();
        entity.setPermissionCode(permissionCode);
        return entity;
    }
}
