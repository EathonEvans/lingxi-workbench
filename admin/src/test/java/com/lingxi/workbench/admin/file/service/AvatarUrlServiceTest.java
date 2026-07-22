package com.lingxi.workbench.admin.file.service;

import com.lingxi.workbench.admin.config.FileUploadProperties;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 头像访问地址解析测试。 */
@ExtendWith(MockitoExtension.class)
class AvatarUrlServiceTest {

    @Mock
    private ObjectStorage objectStorage;

    private AvatarUrlService service;

    @BeforeEach
    void setUp() {
        FileUploadProperties properties = new FileUploadProperties();
        properties.setUrlExpireHours(2);
        service = new AvatarUrlService(objectStorage, properties);
    }

    @Test
    void shouldSignObjectKey() {
        when(objectStorage.presignedGetUrl("avatars/1.png", Duration.ofHours(2)))
                .thenReturn("https://example.com/signed-avatar");

        AvatarUrlService.AvatarUrl result = service.resolve("avatars/1.png");

        assertThat(result.url()).isEqualTo("https://example.com/signed-avatar");
        assertThat(result.objectKey()).isEqualTo("avatars/1.png");
    }

    @Test
    void shouldKeepHistoricalHttpUrl() {
        AvatarUrlService.AvatarUrl result = service.resolve("https://example.com/avatar.png");

        assertThat(result.url()).isEqualTo("https://example.com/avatar.png");
        assertThat(result.objectKey()).isNull();
    }
}
