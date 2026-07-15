package com.lingxi.workbench.admin.auth.service;

import com.lingxi.workbench.admin.auth.model.request.CreateSysUserRequest;
import com.lingxi.workbench.admin.auth.model.request.UpdateSysUserRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysUserVO;

import java.util.List;
import java.util.Set;

/** 系统用户目录业务。 */
public interface SysUserService {

    /** 查询全部系统用户。 */
    List<SysUserVO> list();

    /** 创建系统用户；角色由专用角色分配接口维护。 */
    SysUserVO create(CreateSysUserRequest request);

    /** 更新系统用户基础资料和状态。 */
    SysUserVO update(Long id, UpdateSysUserRequest request);

    /** 记录账号管理端角色变更，供角色业务在关系保存后调用。 */
    void recordRoleAssignment(Long userId, Set<Long> roleIds);
}
