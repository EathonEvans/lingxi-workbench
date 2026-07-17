package com.lingxi.workbench.admin.sys.user.model.converter;

import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;
import com.lingxi.workbench.admin.sys.user.model.vo.SysUserVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 管理员认证模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysUserConverter {

    SysUserBO toBO(SysUserEntity entity);

    SysLoginVO.SysProfileVO toProfileVO(SysUserBO user);

    SysUserVO toVO(SysUserBO user);
}
