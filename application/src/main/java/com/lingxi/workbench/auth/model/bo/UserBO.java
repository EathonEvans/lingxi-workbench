package com.lingxi.workbench.auth.model.bo;

import com.lingxi.workbench.auth.common.enums.UserStatus;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 应用端用户业务对象，隔离数据库实体与认证业务。
 */
@Data
@Builder
@NoArgsConstructor
public class UserBO {

    /** 用户主键。 */
    private Long id;
    /** 登录账号。 */
    private String username;
    /** BCrypt 密码哈希。 */
    private String passwordHash;
    /** 显示名称。 */
    private String displayName;
    /** 头像地址。 */
    private String avatarUrl;
    /** 邮箱地址。 */
    private String email;
    /** 手机号。 */
    private String mobile;
    /** 账号状态。 */
    private UserStatus status;
    /** 是否已开启应用端访问权限。 */
    private Boolean applicationAccessEnabled;

    /**
     * 全参构造器，由 Builder 调用以执行非空校验。
     */
    public UserBO(Long id, String username, String passwordHash, String displayName,
                  String avatarUrl, String email, String mobile,
                  UserStatus status, Boolean applicationAccessEnabled) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.email = email;
        this.mobile = mobile;
        this.status = status;
        this.applicationAccessEnabled = applicationAccessEnabled;
    }

    /**
     * 兼容认证测试和只需要基础账号资料的构造方式。
     */
    public UserBO(Long id, String username, String passwordHash, String displayName,
                  String avatarUrl, UserStatus status) {
        this(id, username, passwordHash, displayName, avatarUrl, null, null, status, true);
    }

    /**
     * 判断账号是否为启用状态。
     *
     * @return true 表示账号已启用
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * 判断用户是否已开启应用端访问权限。
     *
     * @return true 表示已开启访问权限
     */
    public boolean canAccessApplication() {
        return Boolean.TRUE.equals(applicationAccessEnabled);
    }
}
