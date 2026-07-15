package com.lingxi.workbench.admin.auth.service;

import com.lingxi.workbench.admin.auth.model.vo.SysSessionVO;

import java.util.List;

/**
 * 管理端权限装载服务，是 Sa-Token 与业务权限模型之间的适配层。
 */
public interface SysPermissionService {

    /** 查询账号当前拥有的角色编码。 */
    List<String> listRoles(Long userId);

    /** 查询账号当前拥有的功能权限编码。 */
    List<String> listPermissions(Long userId);

    /** 返回账号登录后的完整菜单与权限快照。 */
    SysSessionVO getSession(Long userId);
}
