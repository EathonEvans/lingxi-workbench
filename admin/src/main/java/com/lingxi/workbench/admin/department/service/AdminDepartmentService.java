package com.lingxi.workbench.admin.department.service;

import com.lingxi.workbench.admin.department.model.request.CreateDepartmentRequest;
import com.lingxi.workbench.admin.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.department.model.vo.AdminDepartmentVO;

import java.util.List;
import java.util.Set;

/**
 * 部门业务接口。
 */
public interface AdminDepartmentService {

    /** 查询完整部门树。 */
    List<AdminDepartmentVO> listTree();

    /** 校验一组部门主键是否全部存在，供角色数据范围分配使用。 */
    boolean existsAll(Set<Long> departmentIds);

    /** 新增部门。 */
    AdminDepartmentVO create(CreateDepartmentRequest request);

    /** 修改部门。 */
    AdminDepartmentVO update(Long id, UpdateDepartmentRequest request);

    /** 删除未被引用的部门。 */
    void delete(Long id);
}
