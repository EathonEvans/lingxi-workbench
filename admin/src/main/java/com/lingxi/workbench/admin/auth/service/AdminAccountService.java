package com.lingxi.workbench.admin.auth.service;

import com.lingxi.workbench.admin.auth.model.request.CreateAdminAccountRequest;
import com.lingxi.workbench.admin.auth.model.request.UpdateAdminAccountRequest;
import com.lingxi.workbench.admin.auth.model.vo.AdminAccountVO;

import java.util.List;

/** 后台管理员账号目录业务。 */
public interface AdminAccountService {

    /** 查询全部后台管理员账号。 */
    List<AdminAccountVO> list();

    /** 创建管理员账号；角色由专用角色分配接口维护。 */
    AdminAccountVO create(CreateAdminAccountRequest request);

    /** 更新管理员账号基础资料和状态。 */
    AdminAccountVO update(Long id, UpdateAdminAccountRequest request);
}
