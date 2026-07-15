package com.lingxi.workbench.auth.model.converter;

import com.lingxi.workbench.auth.common.enums.UserAccountStatus;
import com.lingxi.workbench.auth.model.bo.UserAccountBO;
import com.lingxi.workbench.auth.model.entity.UserAccountEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserAccountConverterTest {

    private final UserAccountConverter converter = Mappers.getMapper(UserAccountConverter.class);

    @Test
    void shouldConvertEntityToBO() {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(1L);
        entity.setUsername("alice");
        entity.setPasswordHash("password-hash");
        entity.setDisplayName("Alice");
        entity.setAvatarUrl("avatar-url");
        entity.setStatus("ACTIVE");

        UserAccountBO account = converter.toBO(entity);

        assertEquals(1L, account.id());
        assertEquals("alice", account.username());
        assertEquals("password-hash", account.passwordHash());
        assertEquals("Alice", account.displayName());
        assertEquals("avatar-url", account.avatarUrl());
        assertEquals(UserAccountStatus.ACTIVE, account.status());
    }
}
