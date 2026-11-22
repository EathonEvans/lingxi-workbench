package com.lingxi.workbench.admin.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 管理端菜单节点类型。
 */
@Getter
@RequiredArgsConstructor
public enum MenuType {
    /**
     * 目录
     */
    DIRECTORY("DIRECTORY", "目录"),
    /**
     * 页面
     */
    MENU("MENU", "页面"),
    /**
     * 按钮
     */
    BUTTON("BUTTON", "按钮");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
