package com.lingxi.workbench.auth.model.converter;

import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 用户认证模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserConverter {

    /**
     * 将数据库实体转换为业务对象。
     *
     * @param entity 用户数据库实体
     * @return 用户业务对象
     */
    UserBO toBO(UserEntity entity);

    /**
     * 将业务对象转换为登录响应中的用户资料。
     *
     * @param user              用户业务对象
     * @param resolvedAvatarUrl 浏览器可访问的头像地址
     * @return 用户资料 VO
     */
    @Mapping(target = "avatarUrl", source = "resolvedAvatarUrl")
    UserLoginVO.UserProfileVO toProfileVO(UserBO user, String resolvedAvatarUrl);
}
