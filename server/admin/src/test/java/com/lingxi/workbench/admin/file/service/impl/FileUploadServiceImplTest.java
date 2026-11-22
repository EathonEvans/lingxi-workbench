package com.lingxi.workbench.admin.file.service.impl;

import com.lingxi.workbench.admin.config.FileUploadProperties;
import com.lingxi.workbench.admin.file.common.enums.FileBasePath;
import com.lingxi.workbench.admin.file.model.vo.FileUploadVO;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 通用文件上传安全校验测试。 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceImplTest {

    private static final byte[] PNG_CONTENT = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nL8AAAAASUVORK5CYII=");

    @Mock
    private ObjectStorage objectStorage;

    private FileUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        FileUploadProperties properties = new FileUploadProperties();
        properties.setMaxSize(5 * 1024 * 1024L);
        properties.setUrlExpireHours(1);
        service = new FileUploadServiceImpl(objectStorage, properties);
    }

    @Test
    void shouldUploadDetectedAvatarImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.any", "application/octet-stream", PNG_CONTENT);
        when(objectStorage.presignedGetUrl(anyString(), any(Duration.class)))
                .thenReturn("https://example.com/avatar");

        FileUploadVO result = service.upload(file, FileBasePath.AVATAR, 7L);

        assertThat(result.objectKey())
                .matches("avatars/7/[0-9a-f-]{36}\\.png");
        assertThat(result.url()).isEqualTo("https://example.com/avatar");
        verify(objectStorage).put(result.objectKey(), PNG_CONTENT, "image/png");
        verify(objectStorage).presignedGetUrl(result.objectKey(), Duration.ofHours(1));
    }

    @Test
    void shouldRejectFakeAvatarImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-image".getBytes());

        assertThatThrownBy(() -> service.upload(file, FileBasePath.AVATAR, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持");
        verify(objectStorage, never()).put(any(), any(), any());
    }
}
