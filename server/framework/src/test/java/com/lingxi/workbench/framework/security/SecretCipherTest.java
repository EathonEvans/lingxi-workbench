package com.lingxi.workbench.framework.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretCipherTest {

    private static final String MASTER_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void shouldEncryptAndDecryptSecret() {
        SecretCipher cipher = new SecretCipher(MASTER_KEY);

        SecretCipher.EncryptedSecret encrypted = cipher.encrypt("sk-production-secret");

        assertEquals(
                "sk-production-secret",
                cipher.decrypt(encrypted.ciphertext(), encrypted.iv()));
    }

    @Test
    void shouldUseDifferentIvForEachEncryption() {
        SecretCipher cipher = new SecretCipher(MASTER_KEY);

        SecretCipher.EncryptedSecret first = cipher.encrypt("same-secret");
        SecretCipher.EncryptedSecret second = cipher.encrypt("same-secret");

        assertNotEquals(first.iv(), second.iv());
        assertNotEquals(first.ciphertext(), second.ciphertext());
    }

    @Test
    void shouldFailFastWhenMasterKeyIsMissing() {
        // 主密钥必须来自部署环境，缺失时启动即失败，不允许使用空密钥运行。
        assertThrows(IllegalStateException.class, () -> new SecretCipher(""));
    }

    @Test
    void shouldFailFastWhenMasterKeyHasInvalidLength() {
        String shortKey = Base64.getEncoder()
                .encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> new SecretCipher(shortKey));
    }
}
