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
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDepartmentConverter {

    @Mapping(target = "children", expression = "java(java.util.List.of())")
    SysDepartmentVO toVO(SysDepartmentEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SysDepartmentEntity toEntity(CreateDepartmentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateDepartmentRequest request, @MappingTarget SysDepartmentEntity entity);
}
