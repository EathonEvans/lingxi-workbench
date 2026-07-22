package com.lingxi.workbench.admin.sys.role.model.converter;

import com.lingxi.workbench.admin.sys.role.model.entity.SysRoleEntity;
import com.lingxi.workbench.admin.sys.role.model.request.CreateRoleRequest;
import com.lingxi.workbench.admin.sys.role.model.request.UpdateRoleRequest;
import com.lingxi.workbench.admin.sys.role.model.vo.SysRoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * 角色请求、实体和响应模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysRoleConverter {

    /** 实体转响应模型。 */
    SysRoleVO toVO(SysRoleEntity entity);

    /** 新增请求转实体，忽略主键和时间戳字段。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "builtIn", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SysRoleEntity toEntity(CreateRoleRequest request);

    /** 更新请求合并到已有实体，不覆盖主键、编码和内置标识。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "builtIn", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateRoleRequest request, @MappingTarget SysRoleEntity entity);
}
