package com.lingxi.workbench.auth.model.converter;

import com.lingxi.workbench.auth.model.bo.UserAccountBO;
import com.lingxi.workbench.auth.model.entity.UserAccountEntity;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 用户认证模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserAccountConverter {

    UserAccountBO toBO(UserAccountEntity entity);

    UserLoginVO.UserProfileVO toProfileVO(UserAccountBO account);
}
