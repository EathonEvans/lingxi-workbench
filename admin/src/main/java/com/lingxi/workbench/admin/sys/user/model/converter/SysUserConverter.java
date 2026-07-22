package com.lingxi.workbench.admin.sys.user.model.converter;

import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;
import com.lingxi.workbench.admin.sys.user.model.vo.SysUserVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 系统用户实体、业务对象和响应模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysUserConverter {

    /** 数据库实体转业务对象。 */
    SysUserBO toBO(SysUserEntity entity);

    /** 业务对象转登录后个人信息响应模型。 */
    SysLoginVO.SysProfileVO toProfileVO(SysUserBO user);

    /** 业务对象转用户列表响应模型。 */
    SysUserVO toVO(SysUserBO user);
}
