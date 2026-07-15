package com.lingxi.workbench.admin.role.model.converter;

import com.lingxi.workbench.admin.role.model.entity.AdminRoleEntity;
import com.lingxi.workbench.admin.role.model.request.CreateRoleRequest;
import com.lingxi.workbench.admin.role.model.request.UpdateRoleRequest;
import com.lingxi.workbench.admin.role.model.vo.AdminRoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * 角色请求、实体和响应模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdminRoleConverter {

    AdminRoleVO toVO(AdminRoleEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "builtIn", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdminRoleEntity toEntity(CreateRoleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "builtIn", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateRoleRequest request, @MappingTarget AdminRoleEntity entity);
}
