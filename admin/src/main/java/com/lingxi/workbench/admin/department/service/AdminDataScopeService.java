package com.lingxi.workbench.admin.department.service;

import com.lingxi.workbench.admin.department.model.bo.AdminDataScopeBO;

/**
 * 部门数据权限计算接口。
 * 后续知识库、项目等业务只依赖本接口，不直接读取角色或角色部门关联表。
 */
public interface AdminDataScopeService {

    /** 合并管理员全部启用角色，计算最终数据访问范围。 */
    AdminDataScopeBO resolve(Long accountId);
}
