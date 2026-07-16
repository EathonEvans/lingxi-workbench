package com.lingxi.workbench.admin.model.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelCredentialCipherTest {

    private static final String MASTER_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void shouldEncryptAndDecryptCredential() {
        ModelCredentialCipher cipher = new ModelCredentialCipher(MASTER_KEY);

        ModelCredentialCipher.EncryptedSecret encrypted = cipher.encrypt("sk-production-secret");

        assertEquals("sk-production-secret", cipher.decrypt(encrypted.ciphertext(), encrypted.iv()));
    }

    @Test
    void shouldUseDifferentIvForEachEncryption() {
        ModelCredentialCipher cipher = new ModelCredentialCipher(MASTER_KEY);

        ModelCredentialCipher.EncryptedSecret first = cipher.encrypt("same-secret");
        ModelCredentialCipher.EncryptedSecret second = cipher.encrypt("same-secret");

        assertNotEquals(first.iv(), second.iv());
        assertNotEquals(first.ciphertext(), second.ciphertext());
    }

    @Test
    void shouldRejectMissingMasterKeyWhenCredentialIsUsed() {
        ModelCredentialCipher cipher = new ModelCredentialCipher("");

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));
    }
}
