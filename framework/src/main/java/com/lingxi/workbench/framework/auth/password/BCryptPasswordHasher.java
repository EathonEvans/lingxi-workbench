package com.lingxi.workbench.framework.auth.password;

import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.CharSequenceUtil;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码哈希实现，基于 Sa-Token 内置的 BCrypt 工具。
 *
 * <p>统一维护计算强度，确保管理端生成的密码与用户端校验逻辑保持兼容。</p>
 */
@Component
@SuppressWarnings("deprecation")
public final class BCryptPasswordHasher implements PasswordHasher {

    /** BCrypt 计算强度（log_rounds），数值越大哈希越安全但耗时越长。 */
    private static final int STRENGTH = 12;
    /** 固定 BCrypt 哈希串，用于用户不存在时执行常量时间比对，防止通过响应耗时探测有效账号。 */
    private static final String DUMMY_PASSWORD_HASH = "$2y$12$FA5DZrrOlNthZ7InIh7ETeSW/geeAMPh63WEsjBSwC52li/eZ76By";

    @Override
    public String hash(String rawPassword) {
        Assert.isTrue(CharSequenceUtil.isNotBlank(rawPassword), () -> new IllegalArgumentException("rawPassword must not be blank"));
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(STRENGTH));
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null
                || encodedPassword == null
                || !encodedPassword.startsWith("$2")) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, encodedPassword);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 执行常量时间的虚拟密码比对，用于防止通过响应耗时差异枚举有效账号。
     *
     * @param rawPassword 明文密码
     */
    public void timingSafeDummyMatch(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        matches(rawPassword, DUMMY_PASSWORD_HASH);
    }
}
