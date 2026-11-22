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
 *
 * <p>负责 CreateMenuRequest、UpdateMenuRequest、SysMenuEntity、
 * SysMenuVO 之间的转换，children 字段由业务层填充。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysMenuConverter {

    /**
     * 将菜单实体转换为视图对象，children 初始化为空列表。
     */
    @Mapping(target = "children", expression = "java(java.util.List.of())")
    SysMenuVO toVO(SysMenuEntity entity);

    /**
     * 将新增请求转换为菜单实体，id、createdAt、updatedAt 由框架维护。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SysMenuEntity toEntity(CreateMenuRequest request);

    /**
     * 将修改请求合并到现有实体，code 和 id 保持不变。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateMenuRequest request, @MappingTarget SysMenuEntity entity);
}
