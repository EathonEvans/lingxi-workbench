package com.lingxi.workbench.admin.auth.model.converter;

import com.lingxi.workbench.admin.auth.common.enums.AdminAccountStatus;
import com.lingxi.workbench.admin.auth.model.bo.AdminAccountBO;
import com.lingxi.workbench.admin.auth.model.entity.AdminAccountEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminAccountConverterTest {

    private final AdminAccountConverter converter = Mappers.getMapper(AdminAccountConverter.class);

    @Test
    void shouldConvertEntityToBO() {
        AdminAccountEntity entity = new AdminAccountEntity();
        entity.setId(1L);
        entity.setUsername("admin");
        entity.setPasswordHash("password-hash");
        entity.setDisplayName("系统管理员");
        entity.setAvatarUrl("avatar-url");
        entity.setStatus("ACTIVE");

        AdminAccountBO account = converter.toBO(entity);

        assertEquals(1L, account.id());
        assertEquals("admin", account.username());
        assertEquals("password-hash", account.passwordHash());
        assertEquals("系统管理员", account.displayName());
        assertEquals("avatar-url", account.avatarUrl());
        assertEquals(AdminAccountStatus.ACTIVE, account.status());
    }
}
