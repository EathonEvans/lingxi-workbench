package com.lingxi.workbench.auth.model.converter;

import com.lingxi.workbench.auth.common.enums.UserStatus;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserConverterTest {

    private final UserConverter converter = Mappers.getMapper(UserConverter.class);

    @Test
    void shouldConvertEntityToBO() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setUsername("alice");
        entity.setPasswordHash("password-hash");
        entity.setDisplayName("Alice");
        entity.setAvatarUrl("avatar-url");
        entity.setStatus(UserStatus.ACTIVE);

        UserBO user = converter.toBO(entity);

        assertEquals(1L, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("password-hash", user.getPasswordHash());
        assertEquals("Alice", user.getDisplayName());
        assertEquals("avatar-url", user.getAvatarUrl());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }
}
