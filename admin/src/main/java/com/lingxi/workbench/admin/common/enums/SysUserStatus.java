package com.lingxi.workbench.admin.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 管理端系统用户状态枚举，与 {@link EnableStatus} 相互独立。
 */
@Getter
@RequiredArgsConstructor
public enum SysUserStatus {
    /**
     * 启用
     */
    ACTIVE("ACTIVE", "启用"),
    /**
     * 禁用
     */
    DISABLED("DISABLED", "禁用");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
