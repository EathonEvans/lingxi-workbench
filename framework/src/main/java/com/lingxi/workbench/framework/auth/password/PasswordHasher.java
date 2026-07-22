package com.lingxi.workbench.framework.auth.password;

/**
 * 账号相关应用共享的密码哈希能力。
 */
public interface PasswordHasher {

    /**
     * 对明文密码进行哈希处理。
     *
     * @param rawPassword 明文密码
     * @return 哈希后的密码串
     */
    String hash(String rawPassword);

    /**
     * 校验明文密码与已哈希的密码是否匹配。
     *
     * @param rawPassword     明文密码
     * @param encodedPassword 已哈希的密码串
     * @return 匹配返回 {@code true}，否则返回 {@code false}
     */
    boolean matches(String rawPassword, String encodedPassword);
}
