package com.lingxi.workbench.framework.auth.login;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 登录失败次数限制与账号锁定配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.security.login-protection")
public class LoginProtectionProperties {

    /**
     * 窗口期内允许的最大连续失败次数，达到后触发锁定。
     */
    private int maxFailures = 5;

    /**
     * 失败计数窗口，超过窗口后失败次数重新累计。
     */
    private Duration failureWindow = Duration.ofMinutes(10);

    /**
     * 触发锁定后的冷却时长，期间拒绝登录。
     */
    private Duration lockDuration = Duration.ofMinutes(10);
}
