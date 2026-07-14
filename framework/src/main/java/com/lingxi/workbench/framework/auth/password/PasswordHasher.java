package com.lingxi.workbench.framework.auth.password;

/**
 * 账号相关应用共享的密码哈希能力。
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
