package com.lingxi.workbench.admin.file.common.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 头像对象路径生成与归属校验测试。 */
class FileBasePathTest {

    @Test
    void shouldGenerateUniqueAvatarObjectKey() {
        String first = FileBasePath.AVATAR.resolvePath(7L, "png");
        String second = FileBasePath.AVATAR.resolvePath(7L, "png");

        assertThat(first).matches("avatars/7/[0-9a-f-]{36}\\.png");
        assertThat(second).matches("avatars/7/[0-9a-f-]{36}\\.png");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldOnlyAcceptAvatarOwnedByCurrentUser() {
        String objectKey = "avatars/7/5b6f3f30-e19e-4d02-81d7-5f044575147f.webp";

        assertThat(FileBasePath.AVATAR.ownsAvatar(objectKey, 7L)).isTrue();
        assertThat(FileBasePath.AVATAR.ownsAvatar(objectKey, 8L)).isFalse();
        assertThat(FileBasePath.AVATAR.ownsAvatar("avatars/7/not-a-uuid.webp", 7L)).isFalse();
        assertThat(FileBasePath.AVATAR.ownsAvatar(
                "avatars/7/5b6f3f30-e19e-4d02-81d7-5f044575147f.svg", 7L)).isFalse();
    }
}
