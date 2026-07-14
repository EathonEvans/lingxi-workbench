package com.lingxi.workbench.admin.auth.infrastructure.persistence;

import com.lingxi.workbench.admin.auth.domain.AdminAccount;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 管理员账号持久化对象与领域对象转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdminAccountConverter {

    AdminAccount toDomain(AdminAccountEntity entity);
}
