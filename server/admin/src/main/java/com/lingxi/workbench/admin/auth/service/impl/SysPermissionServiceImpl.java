package com.lingxi.workbench.admin.auth.service.impl;

import com.lingxi.workbench.admin.sys.user.model.vo.SysSessionVO;
import com.lingxi.workbench.admin.auth.service.SysPermissionService;
import com.lingxi.workbench.admin.sys.menu.service.SysMenuService;
import com.lingxi.workbench.admin.sys.role.service.SysRoleService;
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
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;

    /** 查询指定管理员当前拥有的角色编码集合，供 Sa-Token 权限校验使用。 */
    @Override
    @Transactional(readOnly = true)
    public List<String> listRoles(Long userId) {
        return sysRoleService.listRoleCodes(userId);
    }

    /** 查询指定管理员当前拥有的功能权限编码集合，供 Sa-Token 权限校验使用。 */
    @Override
    @Transactional(readOnly = true)
    public List<String> listPermissions(Long userId) {
        return sysMenuService.listPermissionCodes(userId);
    }

    /** 组装管理员登录后的完整权限快照：角色编码、功能权限编码和可访问菜单树。 */
    @Override
    @Transactional(readOnly = true)
    public SysSessionVO getSession(Long userId) {
        return new SysSessionVO(
                listRoles(userId),
                listPermissions(userId),
                sysMenuService.listGrantedTree(userId)
        );
    }
}
