package com.lingxi.workbench.framework.auth.password;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher passwordHasher = new BCryptPasswordHasher();

    @Test
    void shouldHashAndVerifyPassword() {
        String firstHash = passwordHasher.hash("correct-password");
        String secondHash = passwordHasher.hash("correct-password");

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.matches("correct-password", firstHash));
        assertFalse(passwordHasher.matches("wrong-password", firstHash));
    }

    @Test
    void shouldRejectInvalidInputSafely() {
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hash(" "));
        assertFalse(passwordHasher.matches(null, "$2a$12$invalid"));
        assertFalse(passwordHasher.matches("password", null));
        assertFalse(passwordHasher.matches("password", "not-a-bcrypt-hash"));
    }
}
