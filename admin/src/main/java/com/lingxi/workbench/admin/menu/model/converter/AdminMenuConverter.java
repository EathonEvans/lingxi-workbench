package com.lingxi.workbench.admin.menu.model.converter;

import com.lingxi.workbench.admin.menu.model.entity.AdminMenuEntity;
import com.lingxi.workbench.admin.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.menu.model.vo.AdminMenuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * 菜单请求、实体和响应模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdminMenuConverter {

    @Mapping(target = "children", expression = "java(java.util.List.of())")
    AdminMenuVO toVO(AdminMenuEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdminMenuEntity toEntity(CreateMenuRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateMenuRequest request, @MappingTarget AdminMenuEntity entity);
}
