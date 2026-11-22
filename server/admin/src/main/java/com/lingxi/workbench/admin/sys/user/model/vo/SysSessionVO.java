package com.lingxi.workbench.admin.sys.user.model.vo;

import com.lingxi.workbench.admin.sys.menu.model.vo.SysMenuVO;

import java.util.List;

/**
 * 当前管理端会话的授权快照。
 *
 * @param roles       角色编码集合，主要用于角色级判断和前端展示
 * @param permissions 功能权限编码集合，前后端按钮权限均以该集合为准
 * @param menus       后端根据角色计算出的可访问导航树
 */
public record SysSessionVO(
        List<String> roles,
        List<String> permissions,
        List<SysMenuVO> menus
) {
}
