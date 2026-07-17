package com.lingxi.workbench.admin.sys.user.service;

import com.lingxi.workbench.admin.sys.user.model.request.CreateSysUserRequest;
import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import com.lingxi.workbench.admin.sys.user.model.request.UpdateSysUserRequest;
import com.lingxi.workbench.admin.sys.user.model.vo.SysUserVO;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.Set;

/** 系统用户目录业务。 */
public interface SysUserService {

    /** 按条件分页查询系统用户。 */
    PageResult<SysUserVO> page(SysUserPageQuery query);

    /** 创建系统用户；角色由专用角色分配接口维护。 */
    SysUserVO create(CreateSysUserRequest request);

    /** 更新系统用户基础资料和状态。 */
    SysUserVO update(Long id, UpdateSysUserRequest request);

    /** 记录账号管理端角色变更，供角色业务在关系保存后调用。 */
    void recordRoleAssignment(Long userId, Set<Long> roleIds);
}
