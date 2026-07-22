package com.lingxi.workbench.admin.sys.department.service;

import com.lingxi.workbench.admin.sys.department.model.bo.SysDataScopeBO;

/**
 * 部门数据权限计算接口。
 *
 * <p>合并管理员全部启用角色的数据范围，计算最终的可访问部门集合。
 * 后续知识库、项目等业务只依赖本接口，不直接读取角色或角色部门关联表。</p>
 */
public interface SysDataScopeService {

    /**
     * 合并管理员全部启用角色，计算最终数据访问范围。
     *
     * @param userId 管理员主键
     * @return 数据范围计算结果
     */
    SysDataScopeBO resolve(Long userId);
}
