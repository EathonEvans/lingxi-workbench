package com.lingxi.workbench.admin.auth.model.converter;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SysUserConverterTest {

    private final SysUserConverter converter = Mappers.getMapper(SysUserConverter.class);

    @Test
    void shouldConvertEntityToBO() {
        SysUserEntity entity = new SysUserEntity();
        entity.setId(1L);
        entity.setUsername("admin");
        entity.setPasswordHash("password-hash");
        entity.setDisplayName("系统管理员");
        entity.setAvatarUrl("avatar-url");
        entity.setStatus(SysUserStatus.ACTIVE);

        SysUserBO user = converter.toBO(entity);

        assertEquals(1L, user.id());
        assertEquals("admin", user.username());
        assertEquals("password-hash", user.passwordHash());
        assertEquals("系统管理员", user.displayName());
        assertEquals("avatar-url", user.avatarUrl());
        assertEquals(SysUserStatus.ACTIVE, user.status());
    }
}
