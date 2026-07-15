package com.lingxi.workbench.admin.auth.model.converter;

import com.lingxi.workbench.admin.auth.model.bo.AdminAccountBO;
import com.lingxi.workbench.admin.auth.model.entity.AdminAccountEntity;
import com.lingxi.workbench.admin.auth.model.vo.AdminLoginVO;
import com.lingxi.workbench.admin.auth.model.vo.AdminAccountVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 管理员认证模型转换器。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdminAccountConverter {

    AdminAccountBO toBO(AdminAccountEntity entity);

    AdminLoginVO.AdminProfileVO toProfileVO(AdminAccountBO account);

    AdminAccountVO toVO(AdminAccountBO account);
}
