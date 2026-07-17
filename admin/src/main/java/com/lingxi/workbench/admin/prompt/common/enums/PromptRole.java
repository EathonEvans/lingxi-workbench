package com.lingxi.workbench.admin.prompt.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Prompt角色，用于区分模板在消息链路中的职责。 */
@Getter
@RequiredArgsConstructor
public enum PromptRole {
    SYSTEM("SYSTEM", "系统"),
    USER("USER", "用户");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
