package com.lingxi.workbench.auth.model.converter;

import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 用户认证模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserConverter {

    UserBO toBO(UserEntity entity);

    UserLoginVO.UserProfileVO toProfileVO(UserBO user);
}
