package com.lingxi.workbench.admin.domain.model.converter;

import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.model.request.CreateDomainRequest;
import com.lingxi.workbench.admin.domain.model.request.UpdateDomainRequest;
import com.lingxi.workbench.admin.domain.model.vo.DomainVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/** 领域请求、实体与视图转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DomainConverter {

    @Mapping(target = "children", expression = "java(java.util.List.of())")
    DomainVO toVO(DomainEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rootId", ignore = true)
    @Mapping(target = "depth", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DomainEntity toEntity(CreateDomainRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rootId", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "depth", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(UpdateDomainRequest request, @MappingTarget DomainEntity entity);
}
