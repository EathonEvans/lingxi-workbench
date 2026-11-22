package com.lingxi.workbench.admin.sys.department.service;

import com.lingxi.workbench.admin.sys.department.model.request.CreateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.vo.SysDepartmentVO;

import java.util.List;
import java.util.Set;

/**
 * 部门业务接口。
 *
 * <p>负责部门的增删改查及组织树构建，同时提供部门主键批量校验能力供角色数据范围分配使用。</p>
 */
public interface SysDepartmentService {

    /**
     * 查询完整部门树，按排序序号和主键升序排列子节点。
     *
     * @return 部门树根节点列表
     */
    List<SysDepartmentVO> listTree();

    /**
     * 校验一组部门主键是否全部存在。
     *
     * @param departmentIds 部门主键集合
     * @return 全部存在时返回 true
     */
    boolean existsAll(Set<Long> departmentIds);

    /**
     * 新增部门，校验上级部门存在性、编码唯一性和父子关系合法性。
     *
     * @param request 新增部门请求
     * @return 新增后的部门视图
     */
    SysDepartmentVO create(CreateDepartmentRequest request);

    /**
     * 修改部门基础信息或调整上级部门，根部门不允许修改上级和状态。
     *
     * @param id      部门主键
     * @param request 修改请求
     * @return 修改后的部门视图
     */
    SysDepartmentVO update(Long id, UpdateDepartmentRequest request);

    /**
     * 删除部门，需确保无子部门、无管理员归属且无角色引用。
     *
     * @param id 部门主键
     */
    void delete(Long id);
}
