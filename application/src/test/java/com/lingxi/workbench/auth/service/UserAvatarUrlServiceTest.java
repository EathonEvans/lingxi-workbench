package com.lingxi.workbench.auth.service;

import com.lingxi.workbench.framework.storage.ObjectStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAvatarUrlServiceTest {

    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private final UserAvatarUrlService avatarUrlService = new UserAvatarUrlService(objectStorage, 2);

    @Test
    void shouldResolveObjectKeyToPresignedUrl() {
        when(objectStorage.presignedGetUrl("avatars/1/avatar.png", Duration.ofHours(2)))
                .thenReturn("https://storage.example/avatar.png");

        String result = avatarUrlService.resolve("avatars/1/avatar.png");

        assertEquals("https://storage.example/avatar.png", result);
    }

    @Test
    void shouldKeepHistoricalExternalUrl() {
        String result = avatarUrlService.resolve("https://example.com/avatar.png");

        assertEquals("https://example.com/avatar.png", result);
        verify(objectStorage, never()).presignedGetUrl(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void shouldReturnNullForBlankAvatar() {
        assertNull(avatarUrlService.resolve(" "));
        verify(objectStorage, never()).presignedGetUrl(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
