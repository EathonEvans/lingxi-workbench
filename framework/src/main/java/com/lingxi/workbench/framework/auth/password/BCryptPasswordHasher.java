package com.lingxi.workbench.framework.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * BCrypt 密码哈希实现。
 *
 * <p>统一维护计算强度，确保管理端生成的密码与用户端校验逻辑保持兼容。</p>
 */
@Component
public final class BCryptPasswordHasher implements PasswordHasher {

    private static final int STRENGTH = 12;
    private static final int MAX_INPUT_BYTES = 72;
    private static final Pattern BCRYPT_PATTERN = Pattern.compile(
            "\\A\\$2([ayb])?\\$\\d\\d\\$[./0-9A-Za-z]{53}\\z"
    );

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);

    @Override
    public String hash(String rawPassword) {
        requirePassword(rawPassword);
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES
                || encodedPassword == null
                || !BCRYPT_PATTERN.matcher(encodedPassword).matches()) {
            return false;
        }
        return encoder.matches(rawPassword, encodedPassword);
    }

    private static void requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("rawPassword must not exceed 72 UTF-8 bytes");
        }
    }
}
