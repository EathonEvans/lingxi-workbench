package com.lingxi.workbench.auth.infrastructure.persistence;

import com.lingxi.workbench.auth.domain.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 用户账号持久化对象与领域对象转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserAccountConverter {

    UserAccount toDomain(UserAccountEntity entity);
}
