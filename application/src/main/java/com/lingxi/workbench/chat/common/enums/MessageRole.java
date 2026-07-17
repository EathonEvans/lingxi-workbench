package com.lingxi.workbench.chat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 会话消息角色。 */
@Getter
@RequiredArgsConstructor
public enum MessageRole {
    /** 用户输入。 */
    USER("USER", "用户"),
    /** 模型回答。 */
    ASSISTANT("ASSISTANT", "助手");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
