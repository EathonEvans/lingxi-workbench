package com.lingxi.workbench.admin.sys.department.model.converter;

import com.lingxi.workbench.admin.sys.department.model.entity.SysDepartmentEntity;
import com.lingxi.workbench.admin.sys.department.model.request.CreateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.vo.SysDepartmentVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * 部门请求、实体和响应模型转换器。
 *
 * <p>负责 CreateDepartmentRequest、UpdateDepartmentRequest、SysDepartmentEntity、
 * SysDepartmentVO 之间的转换，children 字段由业务层填充。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDepartmentConverter {

    /**
     * 将部门实体转换为视图对象，children 初始化为空列表。
     */
    @Mapping(target = "children", expression = "java(java.util.List.of())")
    SysDepartmentVO toVO(SysDepartmentEntity entity);

    /**
     * 将新增请求转换为部门实体，id、createdAt、updatedAt 由框架维护。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SysDepartmentEntity toEntity(CreateDepartmentRequest request);

    /**
     * 将修改请求合并到现有实体，code 和 id 保持不变。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateDepartmentRequest request, @MappingTarget SysDepartmentEntity entity);
}
