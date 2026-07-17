package com.lingxi.workbench.admin.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
