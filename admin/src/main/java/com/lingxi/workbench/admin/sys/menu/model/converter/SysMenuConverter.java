package com.lingxi.workbench.admin.sys.menu.model.converter;

import com.lingxi.workbench.admin.sys.menu.model.entity.SysMenuEntity;
import com.lingxi.workbench.admin.sys.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.vo.SysMenuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * 菜单请求、实体和响应模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysMenuConverter {

    @Mapping(target = "children", expression = "java(java.util.List.of())")
    SysMenuVO toVO(SysMenuEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SysMenuEntity toEntity(CreateMenuRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateMenuRequest request, @MappingTarget SysMenuEntity entity);
}
