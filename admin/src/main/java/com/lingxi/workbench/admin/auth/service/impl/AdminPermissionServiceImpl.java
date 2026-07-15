package com.lingxi.workbench.admin.auth.service.impl;

import com.lingxi.workbench.admin.auth.model.vo.AdminSessionVO;
import com.lingxi.workbench.admin.auth.service.AdminPermissionService;
import com.lingxi.workbench.admin.menu.service.AdminMenuService;
import com.lingxi.workbench.admin.role.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端权限装载实现。
 *
 * <p>认证模块只依赖角色和菜单的 Service 接口，不跨业务直接访问 Mapper，
 * 从而保持项目约定的 Controller -> Service -> Repo -> Mapper 调用方向。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl implements AdminPermissionService {

    private final AdminRoleService adminRoleService;
    private final AdminMenuService adminMenuService;

    @Override
    @Transactional(readOnly = true)
    public List<String> listRoles(Long accountId) {
        return adminRoleService.listRoleCodes(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listPermissions(Long accountId) {
        return adminMenuService.listPermissionCodes(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSessionVO getSession(Long accountId) {
        return new AdminSessionVO(
                listRoles(accountId),
                listPermissions(accountId),
                adminMenuService.listGrantedTree(accountId)
        );
    }
}
